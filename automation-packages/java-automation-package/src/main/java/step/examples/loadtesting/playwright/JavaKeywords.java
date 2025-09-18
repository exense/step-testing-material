package step.examples.loadtesting.playwright;

import step.handlers.javahandler.Keyword;

public class JavaKeywords extends AbstractJavaLibraryKeyword {

    @Keyword
    public void SimpleJavaKeyword() {
        output.add("Lib_version_userd", USING_LIB_VERSION);
    }
}
