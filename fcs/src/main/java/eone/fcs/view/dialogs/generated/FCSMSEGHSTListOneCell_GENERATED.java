package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMSEGHST;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSMSEGHSTListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGHSTListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSMSEGHST> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMSEGHSTListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGHSTListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGHSTListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMSEGHST> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSMSEGHST> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMSEGHST> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMSEGHST.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSMSEGHST> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMSEGHST>(DOFCSMSEGHST.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSMSEGHST> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMSEGHST>(DOFCSMSEGHST.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMSEGHST> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSMSEGHST,VIFCSMSEGHST>(DOFCSMSEGHST.class,VIFCSMSEGHST.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSMSEGHST itemObject)
                    {
                        return FCSMSEGHSTListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSMSEGHST itemObject)
                    {
                        return FCSMSEGHSTListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMSEGHST> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMSEGHST>(DOFCSMSEGHST.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMSEGHST> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSMSEGHST>(this);
    }
    
    protected String findAvatarIconText(VIFCSMSEGHST itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSMSEGHST itemObject)
    {
        return null;
    }
}
