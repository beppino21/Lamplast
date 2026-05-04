package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCST001Detail_PREV
    implements IPreviewInstanceConfigurator<FCST001Detail>
{
    @Override
    public void configureForPreview(String beanName, FCST001Detail instance)
    {
        DOFCST001 bean = new DOFCST001();
        DCFcst001 dc = new DCFcst001(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
