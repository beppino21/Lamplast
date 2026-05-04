package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSEKETHSTDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETHSTDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSEKETHST>
{
    public FCSEKETHSTDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSEKETHSTDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSEKETHSTDetail}"; }
    public DCFcsekethst getDataContext() { return (DCFcsekethst)super.getDataContext(); }
    public FCSEKETHSTController getController() { return (FCSEKETHSTController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSEKETHST bean = new DOFCSEKETHST();
        DCFcsekethst dc = new DCFcsekethst(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
