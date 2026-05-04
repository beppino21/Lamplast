package eone.fcs.logic.controllers.generated;

import java.util.List;

import org.eclnt.ccee.datacontext.*;
import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.controller.app.IBeanInstanceController;
import org.eclnt.dataapp.view.app.ENUMEditMode;

import org.eclnt.dataapp.controller.app.BeanListControllerBaseDOFW;
import eone.fcs.data.entities.*;
import eone.fcs.data.datacontexts.*;
import eone.fcs.logic.controllers.*;
import eone.fcs.logic.controllers.FCSMSEGHSTController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGHSTListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSMSEGHST,DCDataContext<?>>
{
    public FCSMSEGHSTListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSMSEGHST.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSMSEGHST> readBeans() 
    { 
        return (List<DOFCSMSEGHST>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSMSEGHST createNewItem()
    {
        DOFCSMSEGHST bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSMSEGHST createNewInstance()
    {
        DOFCSMSEGHST bean = new DOFCSMSEGHST();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSMSEGHST bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSMSEGHSTController getControllerForItem(DOFCSMSEGHST item)
    {
        return (FCSMSEGHSTController)super.getControllerForItem(item);
    }
    
    

}
