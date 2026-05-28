package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSSYNCDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSSYNCDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSSYNC>
{
    public FCSSYNCDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSSYNCDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSSYNCDetail}"; }
    public DCFcssync getDataContext() { return (DCFcssync)super.getDataContext(); }
    public FCSSYNCController getController() { return (FCSSYNCController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSSYNC bean = new DOFCSSYNC();
        DCFcssync dc = new DCFcssync(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
