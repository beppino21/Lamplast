package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSEKETHSTDetail_PREV
    implements IPreviewInstanceConfigurator<FCSEKETHSTDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSEKETHSTDetail instance)
    {
        DOFCSEKETHST bean = new DOFCSEKETHST();
        DCFcsekethst dc = new DCFcsekethst(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
