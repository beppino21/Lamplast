package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.UMFORBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMFORBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOUMFOR>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/UMFORBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.UMFORBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOUMFOR selectedBean)
    {
        UMFORDetail result = new UMFORDetail();
        return result;
    }
    
    @Override
    public UMFORListController getListController() { return (UMFORListController)super.getListController(); }
}
