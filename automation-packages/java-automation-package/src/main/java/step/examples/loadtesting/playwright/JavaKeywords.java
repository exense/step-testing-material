package step.examples.loadtesting.playwright;

import step.handlers.javahandler.Keyword;

public class JavaKeywords extends AbstractJavaLibraryKeyword {

    public String AP_VERSION="0.0.0-SNAPSHOT updated 31/10 15h24";

    @Keyword
    public void SimpleJavaKeyword() {
        output.add("Lib_version_used", USING_LIB_VERSION);
        output.add("AP_version_used", AP_VERSION);
    }
}
