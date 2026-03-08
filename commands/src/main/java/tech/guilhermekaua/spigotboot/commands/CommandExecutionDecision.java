package tech.guilhermekaua.spigotboot.commands;

public final class CommandExecutionDecision {
    private static final CommandExecutionDecision CONTINUE = new CommandExecutionDecision(true);
    private static final CommandExecutionDecision STOP = new CommandExecutionDecision(false);

    private final boolean shouldContinue;

    private CommandExecutionDecision(boolean shouldContinue) {
        this.shouldContinue = shouldContinue;
    }

    public static CommandExecutionDecision continueExecution() {
        return CONTINUE;
    }

    public static CommandExecutionDecision stopExecution() {
        return STOP;
    }

    public boolean shouldContinue() {
        return shouldContinue;
    }
}
