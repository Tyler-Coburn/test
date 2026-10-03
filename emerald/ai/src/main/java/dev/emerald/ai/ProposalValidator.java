package dev.emerald.ai;

import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.ProblemType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Schema -> knowledge -> feasibility validation of a parsed proposal. Only a proposal that passes
 * every check becomes a {@link Hypothesis}; the experiment engine still decides truth from the bus.
 */
public final class ProposalValidator {
    /** Observation types a v1 experiment can actually produce for each problem. */
    static final Map<ProblemType, Set<ObservationType>> FEASIBLE = Map.of(
            ProblemType.EGGS_WASTED, EnumSet.of(ObservationType.HOPPER_PULLED));

    private static final Pattern COMMAND = Pattern.compile("(^|\\s)/[a-z_]+");
    private static final Pattern FACT_CLAIM = Pattern.compile(
            "\\b(tested_true|tested_false|adopted|is a fact|proven|already (works|confirmed|true)|confirmed)\\b");
    private static final Pattern GRANT = Pattern.compile("\\b(grant|give (me|us|the village)|spawn (a|an|some)|summon)\\b");

    private ProposalValidator() {
    }

    public static Hypothesis validate(AiProposal p, HypothesisContext ctx, UUID author, long now) {
        if (!AiProposal.PROPOSE_HYPOTHESIS.equals(p.action())) {
            throw new ProposalRejected("action '" + p.action() + "' is not allowed; only PROPOSE_HYPOTHESIS");
        }
        String statement = p.statement() == null ? "" : Normalizer.normalize(p.statement(), Normalizer.Form.NFKC).strip();
        if (statement.isEmpty()) {
            throw new ProposalRejected("empty statement");
        }
        if (statement.codePointCount(0, statement.length()) > HypothesisSchema.MAX_STATEMENT) {
            throw new ProposalRejected("statement longer than " + HypothesisSchema.MAX_STATEMENT);
        }
        if (statement.chars().anyMatch(ch -> Character.isISOControl(ch))) {
            throw new ProposalRejected("statement contains control characters");
        }
        String lower = statement.toLowerCase(Locale.ROOT);
        if (COMMAND.matcher(lower).find()) {
            throw new ProposalRejected("statement contains a command");
        }
        if (GRANT.matcher(lower).find()) {
            throw new ProposalRejected("statement asks for items or entities to be granted");
        }
        if (FACT_CLAIM.matcher(lower).find()) {
            throw new ProposalRejected("statement claims a fact; models may only propose");
        }

        if (p.conceptIds().isEmpty() || p.conceptIds().size() > HypothesisSchema.MAX_CONCEPTS) {
            throw new ProposalRejected("conceptIds must list 1.." + HypothesisSchema.MAX_CONCEPTS + " concepts");
        }
        List<ConceptId> concepts = new ArrayList<>();
        for (String id : p.conceptIds()) {
            if (!ConceptId.isKnownName(id)) {
                throw new ProposalRejected("unknown concept '" + id + "'");
            }
            ConceptId c = ConceptId.valueOf(id);
            if (!concepts.contains(c)) {
                concepts.add(c);
            }
        }
        if (concepts.stream().noneMatch(ctx.knownConcepts()::contains)) {
            throw new ProposalRejected("hypothesis is not grounded in any concept the researcher has observed");
        }

        ObservationType expected;
        try {
            expected = ObservationType.valueOf(p.expectedObservation());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ProposalRejected("unknown observation type '" + p.expectedObservation() + "'");
        }
        Set<ObservationType> feasible = FEASIBLE.getOrDefault(ctx.problem().type(), Set.of());
        if (!feasible.contains(expected)) {
            throw new ProposalRejected(expected + " cannot be produced by an experiment for " + ctx.problem().type());
        }
        ConceptId target = targetFor(expected);
        if (!concepts.contains(target)) {
            concepts.add(target);
        }
        return new Hypothesis(UUID.randomUUID(), author, statement, concepts, target, expected,
                ctx.problem().id(), Hypothesis.Origin.AI, now);
    }

    static ConceptId targetFor(ObservationType expected) {
        return switch (expected) {
            case HOPPER_PULLED -> ConceptId.HOPPER_PULLS_ITEM;
            case ITEM_STORED -> ConceptId.CHEST_STORES_ITEM;
            case ITEM_SPAWNED -> ConceptId.ITEM_ENTITY_SPAWN;
            case CROP_GREW -> ConceptId.CROP_GROWTH;
            case CROP_HARVESTED -> ConceptId.CROP_HARVEST;
            case PISTON_MOVED -> ConceptId.PISTON_MOVES_BLOCK;
            case REDSTONE_SIGNAL -> ConceptId.REDSTONE_SIGNAL;
            case BLOCK_PLACED -> throw new ProposalRejected("BLOCK_PLACED is construction evidence, not a test result");
        };
    }
}
