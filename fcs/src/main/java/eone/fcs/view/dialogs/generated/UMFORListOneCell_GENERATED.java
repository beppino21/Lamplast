package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIUMFOR;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.UMFORListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMFORListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOUMFOR> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public UMFORListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/UMFORListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.UMFORListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOUMFOR> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOUMFOR> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOUMFOR> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOUMFOR.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOUMFOR> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOUMFOR>(DOUMFOR.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOUMFOR> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOUMFOR>(DOUMFOR.class)
        {
            @Override
            protected IDataGridWrapper<DOUMFOR> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOUMFOR,VIUMFOR>(DOUMFOR.class,VIUMFOR.class)
                {
                    @Override
                    protected String findAvatarIconText(VIUMFOR itemObject)
                    {
                        return UMFORListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIUMFOR itemObject)
                    {
                        return UMFORListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOUMFOR> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOUMFOR>(DOUMFOR.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOUMFOR> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOUMFOR>(this);
    }
    
    protected String findAvatarIconText(VIUMFOR itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIUMFOR itemObject)
    {
        return null;
    }
}
