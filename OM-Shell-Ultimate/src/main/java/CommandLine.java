
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a complete command pipeline, optionally connected to subsequent
 * commands via chaining operators (';', '&&', '||') and optionally marked
 * for background execution with '&'.
 */
public class CommandLine {

    public enum Operator {
        NONE,
        SEMICOLON,
        AND_IF,
        OR_IF
    }

    private final List<PipelineStage> stages;
    private final boolean background;
    private Operator nextOperator = Operator.NONE;
    private CommandLine nextCommand = null;

    public CommandLine(List<PipelineStage> stages, boolean background) {
        this.stages = stages != null ? new ArrayList<>(stages) : new ArrayList<>();
        this.background = background;
    }

    public List<PipelineStage> getStages() {
        return Collections.unmodifiableList(stages);
    }

    public boolean isBackground() {
        return background;
    }

    public Operator getNextOperator() {
        return nextOperator;
    }

    public void setNextOperator(Operator nextOperator) {
        this.nextOperator = nextOperator;
    }

    public CommandLine getNextCommand() {
        return nextCommand;
    }

    public void setNextCommand(CommandLine nextCommand) {
        this.nextCommand = nextCommand;
    }

    public boolean isPipeline() {
        return stages.size() > 1;
    }

    public boolean isEmpty() {
        return stages.isEmpty();
    }

    @Override
    public String toString() {
        return "CommandLine{" +
                "stages=" + stages +
                ", background=" + background +
                ", nextOperator=" + nextOperator +
                ", nextCommand=" + (nextCommand != null ? nextCommand.toString() : "null") +
                '}';
    }
}
