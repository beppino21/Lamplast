package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSSYNCDetail_PREV
    implements IPreviewInstanceConfigurator<FCSSYNCDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSSYNCDetail instance)
    {
        DOFCSSYNC bean = new DOFCSSYNC();
        DCFcssync dc = new DCFcssync(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
