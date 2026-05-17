package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSRESTLOG;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSRESTLOGListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSRESTLOGListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSRESTLOG> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSRESTLOGListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSRESTLOGListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSRESTLOGListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSRESTLOG> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSRESTLOG> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSRESTLOG> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSRESTLOG.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSRESTLOG> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSRESTLOG>(DOFCSRESTLOG.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSRESTLOG> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSRESTLOG>(DOFCSRESTLOG.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSRESTLOG> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSRESTLOG,VIFCSRESTLOG>(DOFCSRESTLOG.class,VIFCSRESTLOG.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSRESTLOG itemObject)
                    {
                        return FCSRESTLOGListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSRESTLOG itemObject)
                    {
                        return FCSRESTLOGListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSRESTLOG> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSRESTLOG>(DOFCSRESTLOG.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSRESTLOG> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSRESTLOG>(this);
    }
    
    protected String findAvatarIconText(VIFCSRESTLOG itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSRESTLOG itemObject)
    {
        return null;
    }
}
