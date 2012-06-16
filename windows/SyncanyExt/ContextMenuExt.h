// ContextMenuExt.h : Declaration of the CContextMenuExt

#pragma once
#include "resource.h"       // main symbols
#include "SyncanyExt_i.h"

#if defined(_WIN32_WCE) && !defined(_CE_DCOM) && !defined(_CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA)
#error "Single-threaded COM objects are not properly supported on Windows CE platform, such as the Windows Mobile platforms that do not include full DCOM support. Define _CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA to force ATL to support creating single-thread COM object's and allow use of it's single-threaded COM object implementations. The threading model in your rgs file was set to 'Free' as that is the only threading model supported in non DCOM Windows CE platforms."
#endif

using namespace ATL;

extern HMODULE g_hModule;
extern BOOL g_isExplorer;
extern LONG g_numObject;


// CContextMenuExt

class CContextMenuExt :
    public IShellExtInit,
    public IContextMenu
{
    LONG m_refcnt;
public:
	CContextMenuExt();

	TCHAR m_szFile[MAX_PATH];
    STDMETHODIMP QueryInterface(const IID &riid, void **ppv);

	STDMETHODIMP_(ULONG) AddRef() {
	    InterlockedIncrement(&g_numObject);
	    return InterlockedIncrement(&m_refcnt);
	}

	STDMETHODIMP_(ULONG) Release() {
	    InterlockedDecrement(&g_numObject);
	    ULONG res = InterlockedDecrement(&m_refcnt);
	    if (!res) delete this;
	    return res;
	}

public:
	// IShellExtInit
	STDMETHODIMP Initialize(LPCITEMIDLIST, LPDATAOBJECT, HKEY);

	// IContextMenu
	STDMETHODIMP QueryContextMenu (
		HMENU hmenu, UINT uMenuIndex, UINT uidFirstCmd,
		UINT uidLastCmd, UINT uFlags );

	STDMETHODIMP GetCommandString (
		UINT_PTR idCmd, UINT uFlags, UINT* pwReserved,
		LPSTR pszName, UINT cchMax );

	STDMETHODIMP InvokeCommand (
		LPCMINVOKECOMMANDINFO pCmdInfo );

};