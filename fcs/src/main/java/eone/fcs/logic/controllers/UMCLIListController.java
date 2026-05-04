package eone.fcs.logic.controllers;

import org.eclnt.ccee.ICCEEConstants;
import org.eclnt.ccee.datacontext.*;
import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import eone.fcs.data.datacontexts.*;
import eone.fcs.logic.controllers.generated.*;

public class UMCLIListController
    extends UMCLIListController_GENERATED
    implements ICCEEConstants
{
    public UMCLIListController(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,embeddedId);
    }
}
