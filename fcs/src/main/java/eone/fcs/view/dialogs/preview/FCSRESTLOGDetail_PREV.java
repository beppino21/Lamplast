package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSRESTLOGDetail_PREV
    implements IPreviewInstanceConfigurator<FCSRESTLOGDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSRESTLOGDetail instance)
    {
        DOFCSRESTLOG bean = new DOFCSRESTLOG();
        DCFcsrestlog dc = new DCFcsrestlog(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
