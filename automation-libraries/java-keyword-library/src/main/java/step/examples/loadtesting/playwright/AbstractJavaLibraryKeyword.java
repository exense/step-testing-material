package step.examples.loadtesting.playwright;

import step.handlers.javahandler.AbstractKeyword;
import step.handlers.javahandler.Keyword;

public class AbstractJavaLibraryKeyword extends AbstractKeyword {

    protected String USING_LIB_VERSION="0.0.0-SNAPSHOT updated 23/10 15h20";

    @Keyword
    public void JavaKeywordInLib() {
        output.add("Lib_version_used", USING_LIB_VERSION);
    }


}
