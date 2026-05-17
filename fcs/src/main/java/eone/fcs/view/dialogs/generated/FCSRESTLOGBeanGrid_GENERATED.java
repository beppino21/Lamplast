package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSRESTLOGBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSRESTLOGBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSRESTLOG>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSRESTLOGBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSRESTLOGBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSRESTLOG selectedBean)
    {
        FCSRESTLOGDetail result = new FCSRESTLOGDetail();
        return result;
    }
    
    @Override
    public FCSRESTLOGListController getListController() { return (FCSRESTLOGListController)super.getListController(); }
}
