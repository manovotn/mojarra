/*
 * $Id: ValueChangeListenerTestCase.java,v 1.3 2004/12/14 18:47:16 edburns Exp $
 */

/*
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.faces.jsptest;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlBody;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.sun.faces.htmlunit.AbstractTestCase;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

import javax.faces.component.NamingContainer;

/**
 * <p>Test that invalid values don't cause valueChangeEvents to occur.</p>
 */

public class ValueChangeListenerTestCase extends AbstractTestCase {


    // ------------------------------------------------------------ Constructors


    /**
     * Construct a new instance of this test case.
     *
     * @param name Name of the test case
     */
    public ValueChangeListenerTestCase(String name) {
        super(name);
    }


    // ------------------------------------------------------ Instance Variables


    // ---------------------------------------------------- Overall Test Methods


    /**
     * Return the tests included in this test suite.
     */
    public static Test suite() {
        return (new TestSuite(ValueChangeListenerTestCase.class));
    }

    // ------------------------------------------------- Individual Test Methods
    public void testValueChangeListener() throws Exception {
	HtmlPage page = getPage("/faces/valueChangeListener.jsp");
	List list;
	list = getAllElementsOfGivenClass(page, null, 
					  HtmlTextInput.class); 

	// set the initial value to be 1 for both fields
	((HtmlTextInput)list.get(0)).setValueAttribute("1");
	((HtmlTextInput)list.get(1)).setValueAttribute("1");

	list = getAllElementsOfGivenClass(page, null, 
					  HtmlSubmitInput.class); 
	HtmlSubmitInput button = (HtmlSubmitInput) list.get(0);
	page = (HtmlPage) button.click();

	assertTrue(-1 != 
	   page.asText().indexOf("Received valueChangeEvent for textA"));

	assertTrue(-1 != 
	   page.asText().indexOf("Received valueChangeEvent for textB"));

	// re-submit the form, make sure no valueChangeEvents are fired
	list = getAllElementsOfGivenClass(page, null, 
					  HtmlSubmitInput.class); 
	button = (HtmlSubmitInput) list.get(0);
	page = (HtmlPage) button.click();
	
	assertTrue(-1 == 
	   page.asText().indexOf("Received valueChangeEvent for textA"));

	assertTrue(-1 == 
	   page.asText().indexOf("Received valueChangeEvent for textB"));

	// give invalid values to one field and make sure no
	// valueChangeEvents are fired.
	list = getAllElementsOfGivenClass(page, null, 
					  HtmlTextInput.class); 
	
	((HtmlTextInput)list.get(1)).setValueAttribute("-123");

	list = getAllElementsOfGivenClass(page, null, 
					  HtmlSubmitInput.class); 
	button = (HtmlSubmitInput) list.get(0);
	page = (HtmlPage) button.click();
	
	assertTrue(-1 == 
	   page.asText().indexOf("Received valueChangeEvent for textA"));

	assertTrue(-1 == 
	   page.asText().indexOf("Received valueChangeEvent for textB"));

	assertTrue(-1 != 
	   page.asText().indexOf("Validation Error"));

	// make sure dir and lang are passed through as expected for
	// message and messages
	list = getAllElementsOfGivenClass(page, null, 
					  HtmlSpan.class); 

	boolean 
	    hasMessageContent = false, // do we have the h:message
				       // content we're looking for
	    hasMessagesContent = false; // do we have the h:messages
					// content we're looking for.
	HtmlSpan span = null;

	for (int i = 0; i < list.size(); i++) {
	    span = (HtmlSpan) list.get(i);
	    if (-1 != span.asXml().indexOf("dir=\"LTR\" lang=\"en\" xml:lang=\"en\"")) {
		hasMessagesContent = true;
	    }
	    if (-1 != span.asXml().indexOf("dir=\"RTL\" lang=\"de\" xml:lang=\"de\"")) {
		hasMessageContent = true;
	    }
	}
	assertTrue(hasMessagesContent && hasMessageContent);
	
    }
}
