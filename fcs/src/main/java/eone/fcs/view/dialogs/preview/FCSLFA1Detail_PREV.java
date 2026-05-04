package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSLFA1Detail_PREV
    implements IPreviewInstanceConfigurator<FCSLFA1Detail>
{
    @Override
    public void configureForPreview(String beanName, FCSLFA1Detail instance)
    {
        DOFCSLFA1 bean = new DOFCSLFA1();
        DCFcslfa1 dc = new DCFcslfa1(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
