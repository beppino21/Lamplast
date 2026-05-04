package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSMSEGHSTBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGHSTBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSMSEGHST>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGHSTBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGHSTBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSMSEGHST selectedBean)
    {
        FCSMSEGHSTDetail result = new FCSMSEGHSTDetail();
        return result;
    }
    
    @Override
    public FCSMSEGHSTListController getListController() { return (FCSMSEGHSTListController)super.getListController(); }
}
