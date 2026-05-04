package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSKNA1Detail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSKNA1Detail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSKNA1>
{
    public FCSKNA1Detail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSKNA1Detail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSKNA1Detail}"; }
    public DCFcskna1 getDataContext() { return (DCFcskna1)super.getDataContext(); }
    public FCSKNA1Controller getController() { return (FCSKNA1Controller)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSKNA1 bean = new DOFCSKNA1();
        DCFcskna1 dc = new DCFcskna1(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
