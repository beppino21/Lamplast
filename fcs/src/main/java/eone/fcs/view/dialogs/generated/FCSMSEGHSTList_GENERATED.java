package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.pagebean.IDestroyable;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMSEGHST;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.DefaultBeanListWithEditorListener;
import org.eclnt.dataapp.controller.app.BeanInstanceDataGridControllerDOFW;
import org.eclnt.dataapp.controller.app.IBeanInstanceController;
import org.eclnt.dataapp.controller.app.IBeanDataGridController;
import org.eclnt.dataapp.view.app.datagridmgmt.CCDataGridView2DOFWWrapperWithViewMapping;
import org.eclnt.dataapp.view.app.datagridmgmt.IDataGridWrapper;
import org.eclnt.dataapp.view.app.BeanDataGridWithEditor;

@CCGenClass (expressionBase="#{d.FCSMSEGHSTList}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGHSTList_GENERATED
    extends PageBean
    implements Serializable, IDestroyable
{
    // ------------------------------------------------------------------------
    // inner classes
    // ------------------------------------------------------------------------

    /**
     * Listener class for the grid-part. Extend this listener for listening
     * to more events of the contained grid. The instance if created in method
     * PageBean.createBeanDataGridWithEditorListener.
     */
    protected class MyBeanDataGridWithEditorListener
        extends DefaultBeanListWithEditorListener<DOFCSMSEGHST>
    {
        public MyBeanDataGridWithEditorListener() { super(FCSMSEGHSTList_GENERATED.this); }
        @Override
        public void reactOnGridDataRefreshed() { processUpdatedGridData(); }
    }
        
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridWithEditor<DOFCSMSEGHST> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMSEGHSTList_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGHSTList.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGHSTList}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMSEGHST> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditor();
        IBeanDataGridController<DOFCSMSEGHST> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMSEGHST> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMSEGHST.class,gridController,listener);
    }    

    public void destroy()
    {
        m_gridWithEditor.destroy();
    }

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    /*
     * This is the implementation if you want the grid to use the entity itself - and not the
     * view for the entity:
     * protected BeanDataGridWithEditor<DOFCSMSEGHST> createBeanDataGridWithEditor()
     * {
     *     return new BeanDataGridWithEditor<DOFCSMSEGHST>(DOFCSMSEGHST.class); 
     * }
     */

    /*
     * This is the implementation in which inside the gird the view instances are managed,
     * but the interface (e.g. when selecting an item) is on entity level.
     */    
    protected BeanDataGridWithEditor<DOFCSMSEGHST> createBeanDataGridWithEditor()
    {
        return new BeanDataGridWithEditor<DOFCSMSEGHST>(DOFCSMSEGHST.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMSEGHST> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2DOFWWrapperWithViewMapping<DOFCSMSEGHST,VIFCSMSEGHST>(DOFCSMSEGHST.class,VIFCSMSEGHST.class);
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMSEGHST> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMSEGHST>(DOFCSMSEGHST.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMSEGHST> createBeanDataGridWithEditorListener()
    {
        return new MyBeanDataGridWithEditorListener();
    }
    
    /**
     * Called when the data of the grid is loaded or is changed. When extending the grid columns
     * with own column extensions, this might be the right method to fill the additional data
     * content into the grid items.
     */
    protected void processUpdatedGridData() {}
}
