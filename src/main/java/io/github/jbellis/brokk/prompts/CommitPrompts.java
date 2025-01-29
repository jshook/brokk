package io.github.jbellis.brokk.prompts;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.jbellis.brokk.ContextManager;
import io.github.jbellis.brokk.Environment;

import java.util.List;
import java.util.stream.Collectors;

public abstract class CommitPrompts extends DefaultPrompts {
    public static final CommitPrompts instance = new CommitPrompts() {};

    @Override
    public List<ChatMessage> collectMessages(ContextManager cm) {
        String context = cm.getHistoryMessages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(ContextManager::getText)
                .collect(Collectors.joining("\n\n"));
        context += "\n\n" + Environment.instance.gitDiff();
        var instructions = context + "\n\nHere are my changes, please give me a concise commit message.";
        return List.of(new SystemMessage(systemIntro()),
                       new UserMessage(instructions));
    }

    @Override
    public String systemIntro() {
        return """
               You are an expert software engineer that generates concise,
               one-line Git commit messages based on the provided diffs.
               Review the provided context and diffs which are about to be committed to a git repo.
               Review the diffs carefully.
               Generate a one-line commit message for those changes.
               The commit message should be structured as follows: <type>: <description>
               Use these for <type>: fix, feat, build, chore, ci, docs, style, refactor, perf, test
               
               Ensure the commit message:
               - Starts with the appropriate prefix.
               - Is in the imperative mood (e.g., "Add feature" not "Added feature" or "Adding feature").
               - Does not exceed 72 characters.
               
               Reply only with the one-line commit message, without any additional text, explanations,
               or line breaks.
               """.stripIndent();
    }
}
