package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSSYNCBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSSYNCBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSSYNC>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSSYNCBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSSYNCBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSSYNC selectedBean)
    {
        FCSSYNCDetail result = new FCSSYNCDetail();
        return result;
    }
    
    @Override
    public FCSSYNCListController getListController() { return (FCSSYNCListController)super.getListController(); }
}
