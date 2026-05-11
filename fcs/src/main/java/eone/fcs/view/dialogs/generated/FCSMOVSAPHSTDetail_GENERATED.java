package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSMOVSAPHSTDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPHSTDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSMOVSAPHST>
{
    public FCSMOVSAPHSTDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPHSTDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPHSTDetail}"; }
    public DCFcsmovsaphst getDataContext() { return (DCFcsmovsaphst)super.getDataContext(); }
    public FCSMOVSAPHSTController getController() { return (FCSMOVSAPHSTController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSMOVSAPHST bean = new DOFCSMOVSAPHST();
        DCFcsmovsaphst dc = new DCFcsmovsaphst(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
