package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSEKETDetail_PREV
    implements IPreviewInstanceConfigurator<FCSEKETDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSEKETDetail instance)
    {
        DOFCSEKET bean = new DOFCSEKET();
        DCFcseket dc = new DCFcseket(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
