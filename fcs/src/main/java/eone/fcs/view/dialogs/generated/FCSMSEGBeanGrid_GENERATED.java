package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSMSEGBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSMSEG>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSMSEG selectedBean)
    {
        FCSMSEGDetail result = new FCSMSEGDetail();
        return result;
    }
    
    @Override
    public FCSMSEGListController getListController() { return (FCSMSEGListController)super.getListController(); }
}
