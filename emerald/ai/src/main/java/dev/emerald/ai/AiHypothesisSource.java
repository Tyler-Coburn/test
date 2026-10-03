package dev.emerald.ai;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.village.VillageState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Model-backed hypotheses with the deterministic table as fallback. One request in flight per
 * source (queue depth 1). The server thread only starts calls and inspects finished futures.
 * Any failure (disabled, timeout, transport error, malformed or rejected output) falls back to the
 * hand-authored hypothesis, so turning the model off never stops the loop.
 */
public final class AiHypothesisSource implements HypothesisSource {
    private final AiProvider provider;
    private final AiConfig config;
    private final Function<VillageState, Map<String, Integer>> resources;

    private CompletableFuture<String> inFlight;
    private UUID inFlightProblem;
    private HypothesisContext inFlightContext;
    private long lastCallTick = Long.MIN_VALUE;

    public AiHypothesisSource(AiProvider provider, AiConfig config, Function<VillageState, Map<String, Integer>> resources) {
        this.provider = provider;
        this.config = config;
        this.resources = resources;
    }

    @Override
    public Optional<Hypothesis> next(VillageState v, ResearchProblem problem, CitizenRecord researcher, long now) {
        if (!config.enabled() || provider == null) {
            return HypothesisSource.FALLBACK.next(v, problem, researcher, now);
        }
        if (inFlight == null) {
            if (lastCallTick != Long.MIN_VALUE && now - lastCallTick < config.cooldownTicks()) {
                return HypothesisSource.FALLBACK.next(v, problem, researcher, now);
            }
            inFlightContext = HypothesisContext.of(v, problem, researcher, resources.apply(v));
            inFlightProblem = problem.id();
            lastCallTick = now;
            inFlight = provider.completeJson(HypothesisSchema.json(), HypothesisContext.SYSTEM_PROMPT,
                            inFlightContext.toPrompt())
                    .orTimeout(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            v.log("AI_REQUESTED", now, null, researcher.id(), problem.id(), null, problem.id(),
                    Provenance.AI_PROPOSAL, "provider", provider.name());
            return Optional.empty();
        }
        if (!inFlight.isDone()) {
            return Optional.empty();
        }
        CompletableFuture<String> done = inFlight;
        HypothesisContext ctx = inFlightContext;
        UUID forProblem = inFlightProblem;
        inFlight = null;
        inFlightContext = null;
        inFlightProblem = null;
        if (!problem.id().equals(forProblem)) {
            return Optional.empty();
        }
        try {
            String json = done.getNow(null);
            AiProposal proposal = ProposalParser.parse(json);
            Hypothesis h = ProposalValidator.validate(proposal, ctx, researcher.id(), now);
            v.log("AI_PROPOSAL_ACCEPTED", now, null, researcher.id(), problem.id(), null, problem.id(),
                    Provenance.AI_PROPOSAL, "statement", h.statement(), "expected", h.expected().name());
            return Optional.of(h);
        } catch (ProposalRejected e) {
            return fallback(v, problem, researcher, now, "rejected: " + e.getMessage());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String reason = cause instanceof ProposalRejected r ? "rejected: " + r.getMessage()
                    : "call failed: " + cause.getClass().getSimpleName();
            return fallback(v, problem, researcher, now, reason);
        }
    }

    private Optional<Hypothesis> fallback(VillageState v, ResearchProblem problem, CitizenRecord researcher,
                                          long now, String reason) {
        v.log("AI_PROPOSAL_REJECTED", now, null, researcher.id(), problem.id(), null, problem.id(),
                Provenance.AI_PROPOSAL, "reason", reason, "fallback", "table");
        return HypothesisSource.FALLBACK.next(v, problem, researcher, now);
    }

    public boolean busy() {
        return inFlight != null && !inFlight.isDone();
    }
}
