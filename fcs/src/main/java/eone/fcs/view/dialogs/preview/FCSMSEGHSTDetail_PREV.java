package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSMSEGHSTDetail_PREV
    implements IPreviewInstanceConfigurator<FCSMSEGHSTDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSMSEGHSTDetail instance)
    {
        DOFCSMSEGHST bean = new DOFCSMSEGHST();
        DCFcsmseghst dc = new DCFcsmseghst(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
