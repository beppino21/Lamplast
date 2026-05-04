package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSMSEGHSTDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGHSTDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSMSEGHST>
{
    public FCSMSEGHSTDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGHSTDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGHSTDetail}"; }
    public DCFcsmseghst getDataContext() { return (DCFcsmseghst)super.getDataContext(); }
    public FCSMSEGHSTController getController() { return (FCSMSEGHSTController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSMSEGHST bean = new DOFCSMSEGHST();
        DCFcsmseghst dc = new DCFcsmseghst(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
