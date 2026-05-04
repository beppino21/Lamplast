package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCST001Detail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCST001Detail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCST001>
{
    public FCST001Detail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCST001Detail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCST001Detail}"; }
    public DCFcst001 getDataContext() { return (DCFcst001)super.getDataContext(); }
    public FCST001Controller getController() { return (FCST001Controller)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCST001 bean = new DOFCST001();
        DCFcst001 dc = new DCFcst001(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
