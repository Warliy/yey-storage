// ShellIconExt.h : Declaration of the CShellIconExt

// REF: lp:tortoisebzr/shellext/IconOverlay.h
// This file largely copied/modified to suit purposes from the TortoiseBzr project. Thank you!

#pragma once
#include "resource.h"       // main symbols
#include "stdafx.h"



#include "SyncanyExt_i.h"



#if defined(_WIN32_WCE) && !defined(_CE_DCOM) && !defined(_CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA)
#error "Single-threaded COM objects are not properly supported on Windows CE platform, such as the Windows Mobile platforms that do not include full DCOM support. Define _CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA to force ATL to support creating single-thread COM object's and allow use of it's single-threaded COM object implementations. The threading model in your rgs file was set to 'Free' as that is the only threading model supported in non DCOM Windows CE platforms."
#endif

using namespace ATL;

extern HMODULE g_hModule;
extern BOOL g_isExplorer;
extern LONG g_numObject;



// CShellIconExt

class CShellIconExt :
	public IShellIconOverlayIdentifier
{
public:
	CShellIconExt();

public:
	/*
    STDMETHODIMP_(ULONG) AddRef() {
        InterlockedIncrement(g_numObject);
        return InterlockedIncrement(&m_refcnt);
    }
    STDMETHODIMP_(ULONG) Release() {
        InterlockedDecrement(g_numObject);
        ULONG res = InterlockedDecrement(&m_refcnt);
        if (!res) delete this;
        return res;
    }
	*/

    STDMETHODIMP QueryInterface(const IID &riid, void **ppv);

	// IShellExtInit
	STDMETHODIMP Initialize(LPCITEMIDLIST, LPDATAOBJECT, HKEY);

	// IShellIconOverlayIdentifier
	STDMETHODIMP GetOverlayInfo(LPWSTR pwszIconFile, int cchMax, int *pIndex, DWORD *pdwFlags);
    STDMETHODIMP GetPriority(int *pPriority);
    STDMETHODIMP IsMemberOf(LPCWSTR pwszPath, DWORD dwAttrib);

protected:
	virtual DWORD GetMyState() = 0;
	virtual const TCHAR *GetMyStateName() = 0;
    LONG m_refcnt;

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


    enum {
        STATE_NOTHING, 
        STATE_UNVERSIONED,
        //STATE_IGNORED,
        STATE_UNCHANGED,
        //STATE_PROGRESS,
        STATE_MODIFIED,
        //STATE_ADDED,
        //STATE_DELETED,
        //STATE_MISSING,
        //STATE_CONFLICT,
    };
};

//OBJECT_ENTRY_AUTO(__uuidof(ShellIconExt), CShellIconExt)

#define MAKE_OVERLAY_CLASS(TYPE, STATE) \
class CShellIconExt_##TYPE : \
	public CShellIconExt \
{ \
public: \
	virtual DWORD GetMyState() { return STATE; } \
	virtual const TCHAR *GetMyStateName() { return _T(#TYPE); } \
};

MAKE_OVERLAY_CLASS(Normal, STATE_NOTHING);
MAKE_OVERLAY_CLASS(Unchanged, STATE_UNCHANGED);
MAKE_OVERLAY_CLASS(Modified, STATE_MODIFIED);
MAKE_OVERLAY_CLASS(Unversioned, STATE_UNVERSIONED);