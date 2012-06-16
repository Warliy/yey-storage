

/* this ALWAYS GENERATED file contains the definitions for the interfaces */


 /* File created by MIDL compiler version 7.00.0555 */
/* at Mon May 30 23:57:41 2011
 */
/* Compiler settings for SyncanyExt.idl:
    Oicf, W1, Zp8, env=Win32 (32b run), target_arch=X86 7.00.0555 
    protocol : dce , ms_ext, c_ext, robust
    error checks: allocation ref bounds_check enum stub_data 
    VC __declspec() decoration level: 
         __declspec(uuid()), __declspec(selectany), __declspec(novtable)
         DECLSPEC_UUID(), MIDL_INTERFACE()
*/
/* @@MIDL_FILE_HEADING(  ) */

#pragma warning( disable: 4049 )  /* more than 64k source lines */


/* verify that the <rpcndr.h> version is high enough to compile this file*/
#ifndef __REQUIRED_RPCNDR_H_VERSION__
#define __REQUIRED_RPCNDR_H_VERSION__ 475
#endif

#include "rpc.h"
#include "rpcndr.h"

#ifndef __RPCNDR_H_VERSION__
#error this stub requires an updated version of <rpcndr.h>
#endif // __RPCNDR_H_VERSION__

#ifndef COM_NO_WINDOWS_H
#include "windows.h"
#include "ole2.h"
#endif /*COM_NO_WINDOWS_H*/

#ifndef __SyncanyExt_i_h__
#define __SyncanyExt_i_h__

#if defined(_MSC_VER) && (_MSC_VER >= 1020)
#pragma once
#endif

/* Forward Declarations */ 

#ifndef __IContextMenuExt_FWD_DEFINED__
#define __IContextMenuExt_FWD_DEFINED__
typedef interface IContextMenuExt IContextMenuExt;
#endif 	/* __IContextMenuExt_FWD_DEFINED__ */


#ifndef __IShellIconExt_FWD_DEFINED__
#define __IShellIconExt_FWD_DEFINED__
typedef interface IShellIconExt IShellIconExt;
#endif 	/* __IShellIconExt_FWD_DEFINED__ */


#ifndef __ContextMenuExt_FWD_DEFINED__
#define __ContextMenuExt_FWD_DEFINED__

#ifdef __cplusplus
typedef class ContextMenuExt ContextMenuExt;
#else
typedef struct ContextMenuExt ContextMenuExt;
#endif /* __cplusplus */

#endif 	/* __ContextMenuExt_FWD_DEFINED__ */


#ifndef __ShellIconExt_FWD_DEFINED__
#define __ShellIconExt_FWD_DEFINED__

#ifdef __cplusplus
typedef class ShellIconExt ShellIconExt;
#else
typedef struct ShellIconExt ShellIconExt;
#endif /* __cplusplus */

#endif 	/* __ShellIconExt_FWD_DEFINED__ */


/* header files for imported files */
#include "oaidl.h"
#include "ocidl.h"

#ifdef __cplusplus
extern "C"{
#endif 


#ifndef __IContextMenuExt_INTERFACE_DEFINED__
#define __IContextMenuExt_INTERFACE_DEFINED__

/* interface IContextMenuExt */
/* [unique][uuid][object] */ 


EXTERN_C const IID IID_IContextMenuExt;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("4A362310-A3AA-4B47-A629-2F6F314B771B")
    IContextMenuExt : public IUnknown
    {
    public:
    };
    
#else 	/* C style interface */

    typedef struct IContextMenuExtVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IContextMenuExt * This,
            /* [in] */ REFIID riid,
            /* [annotation][iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IContextMenuExt * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IContextMenuExt * This);
        
        END_INTERFACE
    } IContextMenuExtVtbl;

    interface IContextMenuExt
    {
        CONST_VTBL struct IContextMenuExtVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IContextMenuExt_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IContextMenuExt_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IContextMenuExt_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IContextMenuExt_INTERFACE_DEFINED__ */


#ifndef __IShellIconExt_INTERFACE_DEFINED__
#define __IShellIconExt_INTERFACE_DEFINED__

/* interface IShellIconExt */
/* [unique][uuid][object] */ 


EXTERN_C const IID IID_IShellIconExt;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("D720C2C7-4EC5-4E78-AD33-E792A5A6F188")
    IShellIconExt : public IUnknown
    {
    public:
    };
    
#else 	/* C style interface */

    typedef struct IShellIconExtVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IShellIconExt * This,
            /* [in] */ REFIID riid,
            /* [annotation][iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IShellIconExt * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IShellIconExt * This);
        
        END_INTERFACE
    } IShellIconExtVtbl;

    interface IShellIconExt
    {
        CONST_VTBL struct IShellIconExtVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IShellIconExt_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IShellIconExt_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IShellIconExt_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IShellIconExt_INTERFACE_DEFINED__ */



#ifndef __SyncanyExtLib_LIBRARY_DEFINED__
#define __SyncanyExtLib_LIBRARY_DEFINED__

/* library SyncanyExtLib */
/* [version][uuid] */ 


EXTERN_C const IID LIBID_SyncanyExtLib;

EXTERN_C const CLSID CLSID_ContextMenuExt;

#ifdef __cplusplus

class DECLSPEC_UUID("8E65E24B-A16C-4DDB-ABE9-D4940C657648")
ContextMenuExt;
#endif

EXTERN_C const CLSID CLSID_ShellIconExt;

#ifdef __cplusplus

class DECLSPEC_UUID("9B527650-9FC6-4082-B8D9-9CB63B34D65E")
ShellIconExt;
#endif
#endif /* __SyncanyExtLib_LIBRARY_DEFINED__ */

/* Additional Prototypes for ALL interfaces */

/* end of Additional Prototypes */

#ifdef __cplusplus
}
#endif

#endif


