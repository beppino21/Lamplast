package eone.fcs.logic.controllers;

import org.eclnt.ccee.ICCEEConstants;
import org.eclnt.ccee.datacontext.*;
import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import eone.fcs.data.datacontexts.*;
import eone.fcs.logic.controllers.generated.*;

public class FCSMSEGHSTListController
    extends FCSMSEGHSTListController_GENERATED
    implements ICCEEConstants
{
    public FCSMSEGHSTListController(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,embeddedId);
    }
}
