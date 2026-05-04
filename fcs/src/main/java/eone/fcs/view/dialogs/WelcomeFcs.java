package eone.fcs.view.dialogs;

import org.eclnt.ccda_base.logic.logic.*;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.component.PageBeanComponent;

/*
 * Generated fisrt version of welcome screen that is shown in the
 * content area of the workplace. You may update/change according
 * to your needs.
 */
 
@CCGenClass (expressionBase="#{d.WelcomeFcs}")
public class WelcomeFcs
    extends PageBeanComponent 
{
    String m_welcomeImage;
    String m_welcomeHeadline;
    String m_welcomeTitle;
    String m_welcomeDescription;

    public WelcomeFcs()
    {
        m_welcomeImage = WorkplaceLogic.instance().findWorkplaceText(ENUMWorkplaceTextId.WELCOME_IMAGE);
        m_welcomeHeadline = WorkplaceLogic.instance().findWorkplaceText(ENUMWorkplaceTextId.WELCOME_HEADLINE);
        m_welcomeTitle = WorkplaceLogic.instance().findWorkplaceText(ENUMWorkplaceTextId.WELCOME_TITLE);
        m_welcomeDescription = WorkplaceLogic.instance().findWorkplaceText(ENUMWorkplaceTextId.WELCOME_DESCRIPTION);
    }
    
    public String getRootExpressionUsedInPage() { return "#{d.WelcomeFcs}"; }
    
    public String getWelcomeImage() { return m_welcomeImage; }
    public String getWelcomeHeadline() { return m_welcomeHeadline; } 
    public String getWelcomeTitle() { return m_welcomeTitle; } 
    public String getWelcomeDescription() { return m_welcomeDescription; }
}
