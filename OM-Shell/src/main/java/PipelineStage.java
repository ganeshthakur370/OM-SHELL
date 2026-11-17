import java.util.ArrayList;

import java.util.Collections;

import java.util.List;



public class PipelineStage {

    private final List<String> words;

    private final List<Redirection> redirections;



    public PipelineStage(List<String> words, List<Redirection> redirections) {

        this.words = words != null ? new ArrayList<>(words) : new ArrayList<>();

        this.redirections = redirections != null ? new ArrayList<>(redirections) : new ArrayList<>();

    }



    public List<String> getWords() {

        return Collections.unmodifiableList(words);

    }



    public List<Redirection> getRedirections() {

        return Collections.unmodifiableList(redirections);

    }



    public String getCommandName() {

        return words.isEmpty() ? "" : words.get(0);

    }



    public List<String> getArguments() {

        if (words.size() <= 1) {

            return Collections.emptyList();

        }

        return Collections.unmodifiableList(words.subList(1, words.size()));

    }



    public boolean isEmpty() {

        return words.isEmpty() && redirections.isEmpty();

    }



    @Override

    public String toString() {

        return "PipelineStage{words=" + words + ", redirections=" + redirections + "}";

    }

}
