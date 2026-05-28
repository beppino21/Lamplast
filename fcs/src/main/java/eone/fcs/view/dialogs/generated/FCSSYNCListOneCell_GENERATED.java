package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSSYNC;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSSYNCListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSSYNCListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSSYNC> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSSYNCListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSSYNCListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSSYNCListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSSYNC> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSSYNC> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSSYNC> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSSYNC.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSSYNC> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSSYNC>(DOFCSSYNC.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSSYNC> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSSYNC>(DOFCSSYNC.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSSYNC> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSSYNC,VIFCSSYNC>(DOFCSSYNC.class,VIFCSSYNC.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSSYNC itemObject)
                    {
                        return FCSSYNCListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSSYNC itemObject)
                    {
                        return FCSSYNCListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSSYNC> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSSYNC>(DOFCSSYNC.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSSYNC> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSSYNC>(this);
    }
    
    protected String findAvatarIconText(VIFCSSYNC itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSSYNC itemObject)
    {
        return null;
    }
}
