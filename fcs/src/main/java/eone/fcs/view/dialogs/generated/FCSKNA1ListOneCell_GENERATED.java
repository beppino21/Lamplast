package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSKNA1;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSKNA1ListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSKNA1ListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSKNA1> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSKNA1ListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSKNA1ListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSKNA1ListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSKNA1> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSKNA1> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSKNA1> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSKNA1.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSKNA1> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSKNA1>(DOFCSKNA1.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSKNA1> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSKNA1>(DOFCSKNA1.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSKNA1> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSKNA1,VIFCSKNA1>(DOFCSKNA1.class,VIFCSKNA1.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSKNA1 itemObject)
                    {
                        return FCSKNA1ListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSKNA1 itemObject)
                    {
                        return FCSKNA1ListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSKNA1> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSKNA1>(DOFCSKNA1.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSKNA1> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSKNA1>(this);
    }
    
    protected String findAvatarIconText(VIFCSKNA1 itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSKNA1 itemObject)
    {
        return null;
    }
}
