package eone.listinoSD.view.managedbeans;

import java.io.Serializable;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.jsfserver.base.faces.event.ActionEvent;

@CCGenClass (expressionBase="#{d.MainUI}")

public class MainUI
    extends PageBean 
    implements Serializable
{
    // ------------------------------------------------------------------------
    // inner classes
    // ------------------------------------------------------------------------
    
    /* Listener to the user of the page bean. */
    public interface IListener extends Serializable
    {
    }
    
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------
    
    private IListener m_listener;
    
    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public MainUI()
    {
    }

    public String getPageName() { return "/main.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.MainUI}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    /* Initialization of the bean. Add any parameter that is required within your scenario. */
    public void prepare(IListener listener)
    {
        m_listener = listener;
    }

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
}
