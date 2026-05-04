package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSMOVSAPBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSMOVSAP>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSMOVSAP selectedBean)
    {
        FCSMOVSAPDetail result = new FCSMOVSAPDetail();
        return result;
    }
    
    @Override
    public FCSMOVSAPListController getListController() { return (FCSMOVSAPListController)super.getListController(); }
}
