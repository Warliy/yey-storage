// ShellIconExt.cpp : Implementation of CShellIconExt

#include "stdafx.h"
#include "ShellIconExt.h"

STDMETHODIMP CShellIconExt::Initialize( LPCITEMIDLIST pidlFolder, LPDATAOBJECT pDataObj, HKEY hProgID )
{

	return S_OK;
}

STDMETHODIMP CShellIconExt::GetOverlayInfo
	( LPWSTR pwszIconFile, int cchMax, int *pIndex, DWORD *pdwFlags)
{
	GetModuleFileNameW(_AtlBaseModule.GetModuleInstance(), pwszIconFile, cchMax);
	*pIndex = 0;
	*pdwFlags = ISIOI_ICONFILE | ISIOI_ICONINDEX;

	return S_OK;
}

STDMETHODIMP CShellIconExt::GetPriority(int *pPriority)
{
	*pPriority = 0;
	return S_OK;
}

STDMETHODIMP CShellIconExt::IsMemberOf(LPCWSTR pwszPath, DWORD dwAttrib)
{
	DWORD mystate = GetMyState();
	return S_FALSE;
}

CShellIconExt::CShellIconExt() : m_refcnt(0)
{
}

STDMETHODIMP CShellIconExt::QueryInterface(const IID &riid, void **ppv)
{
    if (!ppv)
        return E_POINTER;
    *ppv = NULL;

    if (IsEqualIID(riid, IID_IShellExtInit) || IsEqualIID(riid, IID_IUnknown)) {
        *ppv = (LPSHELLEXTINIT) this;
    }
    else if (IsEqualIID(riid, IID_IShellIconOverlayIdentifier)) {
        *ppv = (IShellIconOverlayIdentifier*) this;
    }
    if (*ppv) {
        AddRef();
        return NOERROR;
    }

    return E_NOINTERFACE;
}

// CShellIconExt

