package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSRESTLOGDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSRESTLOGDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSRESTLOG>
{
    public FCSRESTLOGDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSRESTLOGDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSRESTLOGDetail}"; }
    public DCFcsrestlog getDataContext() { return (DCFcsrestlog)super.getDataContext(); }
    public FCSRESTLOGController getController() { return (FCSRESTLOGController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSRESTLOG bean = new DOFCSRESTLOG();
        DCFcsrestlog dc = new DCFcsrestlog(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
