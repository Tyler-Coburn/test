package dev.emerald.ai;

/** A model response that must not become game state. The message is the logged reason. */
public class ProposalRejected extends RuntimeException {
    public ProposalRejected(String reason) {
        super(reason);
    }
}
