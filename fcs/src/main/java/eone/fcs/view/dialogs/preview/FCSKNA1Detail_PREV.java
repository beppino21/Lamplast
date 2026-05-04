package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSKNA1Detail_PREV
    implements IPreviewInstanceConfigurator<FCSKNA1Detail>
{
    @Override
    public void configureForPreview(String beanName, FCSKNA1Detail instance)
    {
        DOFCSKNA1 bean = new DOFCSKNA1();
        DCFcskna1 dc = new DCFcskna1(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
