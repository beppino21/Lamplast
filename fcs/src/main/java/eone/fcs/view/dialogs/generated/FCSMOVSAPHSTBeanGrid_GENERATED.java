package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSMOVSAPHSTBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPHSTBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSMOVSAPHST>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPHSTBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPHSTBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSMOVSAPHST selectedBean)
    {
        FCSMOVSAPHSTDetail result = new FCSMOVSAPHSTDetail();
        return result;
    }
    
    @Override
    public FCSMOVSAPHSTListController getListController() { return (FCSMOVSAPHSTListController)super.getListController(); }
}
