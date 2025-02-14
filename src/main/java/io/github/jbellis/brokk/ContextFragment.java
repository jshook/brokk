package io.github.jbellis.brokk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

public interface ContextFragment {
    String source(); // display name for interaction with commands
    String description(); // longer description displayed to user
    String text() throws IOException; // content
    String format() throws IOException; // format for LLM
    Set<String> classnames(Analyzer analyzer);


    record PathFragment(RepoFile file) implements ContextFragment {
        @Override
        public String source() {
            return file.getFileName();
        }

        @Override
        public String text() throws IOException {
            return file.read();
        }

        @Override
        public String description() {
            return file.getParent();
        }

        @Override
        public String format() throws IOException {
            return """
            <file path="%s">
            %s
            </file>
            """.formatted(file.toString(), text()).stripIndent();
        }

        @Override
        public Set<String> classnames(Analyzer analyzer) {
            return analyzer.classesInFile(file);
        }

        @Override
        public String toString() {
            return "PathFragment('%s')".formatted(file);
        }
    }

    abstract class VirtualFragment implements ContextFragment {
        private int position;

        protected VirtualFragment(int position) {
            this.position = position;
        }

        public int position() {
            return position;
        }

        @Override
        public String source() {
            // 1-based label in brackets
            return "%d".formatted(position + 1);
        }

        public final void renumber(int newPosition) {
            this.position = newPosition;
        }

        @Override
        public String format() throws IOException {
            return """
            <fragment id="%d" description="%s">
            %s
            </fragment>
            """.formatted(position, description(), text()).stripIndent();
        }

        @Override
        public Set<String> classnames(Analyzer analyzer) {
            return ContextManager.getTrackedFiles().stream().parallel()
                    .filter(f -> text().contains(f.toString()))
                    .flatMap(f -> analyzer.classesInFile(f).stream())
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public abstract String text(); // no exceptions
    }

    class StringFragment extends VirtualFragment {
        private final String text;
        private final String description;

        public StringFragment(int position, String text, String description) {
            super(position);
            this.text = text;
            this.description = description;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String toString() {
            return "StringFragment('%s')".formatted(description);
        }
    }

    class PasteFragment extends VirtualFragment {
        private final String text;
        private final Future<String> descriptionFuture;

        public PasteFragment(int position, String text, Future<String> descriptionFuture) {
            super(position);
            this.text = text;
            this.descriptionFuture = descriptionFuture;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public String description() {
            if (descriptionFuture.isDone()) {
                try {
                    return "Paste of " + descriptionFuture.get();
                } catch (Exception e) {
                    return "(Error summarizing paste)";
                }
            }
            return "(Summarizing. This does not block LLM requests)";
        }

        @Override
        public String toString() {
            return "PasteFragment('%s')".formatted(description());
        }
    }

    class StacktraceFragment extends VirtualFragment {
        private final Set<String> classnames;
        private final String original;
        private final String exception;
        private final String code;

        public StacktraceFragment(int position, Set<String> classnames, String original, String exception, String code) {
            super(position);
            this.classnames = classnames;
            this.original = original;
            this.exception = exception;
            this.code = code;
        }

        @Override
        public String text() {
            return original + "\n\nStacktrace methods in this project:\n\n" + code;
        }

        @Override
        public Set<String> classnames(Analyzer analyzer) {
            return classnames;
        }

        @Override
        public String description() {
            return "stacktrace of " + exception;
        }
    }

    static String toClassname(String methodname) {
        int lastDot = methodname.lastIndexOf('.');
        if (lastDot == -1) {
            return methodname;
        }
        return methodname.substring(0, lastDot);
    }

    class UsageFragment extends VirtualFragment {
        private final String targetIdentifier;
        private final Set<String> classnames;
        private final String code;

        public UsageFragment(int position, String targetIdentifier, Set<String> classnames, String code) {
            super(position);
            this.targetIdentifier = targetIdentifier;
            this.classnames = classnames;
            this.code = code;
        }

        @Override
        public String text() {
            return code;
        }

        @Override
        public Set<String> classnames(Analyzer analyzer) {
            return classnames;
        }

        @Override
        public String description() {
            return "Uses of %s".formatted(targetIdentifier);
        }
    }

    class SkeletonFragment extends VirtualFragment {
        private final List<String> shortClassnames;
        private final Set<String> classnames;
        private final String skeletonText;

        public SkeletonFragment(int position, List<String> shortClassnames, Set<String> classnames, String skeletonText) {
            super(position);
            this.shortClassnames = shortClassnames;
            this.classnames = classnames;
            this.skeletonText = skeletonText;
        }

        @Override
        public String text() {
            return skeletonText;
        }

        @Override
        public Set<String> classnames(Analyzer analyzer) {
            return classnames;
        }

        @Override
        public String description() {
            return "Summary of " + String.join(", ", shortClassnames.stream().sorted().toList());
        }

        @Override
        public String format() throws IOException {
            return """
            <summary classes="%s">
            %s
            </summary>
            """.formatted(String.join(", ", classnames.stream().sorted().toList()), text()).stripIndent();
        }

        @Override
        public String toString() {
            return "SkeletonFragment('%s')".formatted(description());
        }
    }

    /**
     * A context fragment that holds a list of short class names and a text
     * representation (e.g. skeletons) of those classes.
     */
    class AutoContext implements ContextFragment {
        public static final AutoContext EMPTY = new AutoContext(List.of(new SkeletonFragment(-1, List.of("Enabled, but no references found"), Set.of(), "")));
        public static final AutoContext DISABLED  = new AutoContext(List.of());

        private final List<SkeletonFragment> skeletons;

        public AutoContext(List<SkeletonFragment> skeletons) {
            this.skeletons = skeletons;
        }

        @Override
        public String source() {
            return "0 [Auto]";
        }

        @Override
        public String text() {
            return String.join("\n\n", skeletons.stream().map(SkeletonFragment::text).toList());
        }

        @Override
        public Set<String> classnames(Analyzer analyzer) {
            return skeletons.stream().flatMap(s -> s.classnames.stream()).collect(java.util.stream.Collectors.toSet());
        }

        /**
         * Returns a comma-separated list of short class names (no package).
         */
        @Override
        public String description() {
            return String.join(", ", skeletons.stream().flatMap(s -> s.shortClassnames.stream()).toList());
        }

        @Override
        public String format() throws IOException {
            String st = "";
            for (SkeletonFragment s : skeletons) {
                if (!st.isEmpty()) {
                    st += "\n\n";
                }
                st += s.format();
            }
            return st;
        }

        @Override
        public String toString() {
            return "AutoContext('%s')".formatted(description());
        }
    }
}
