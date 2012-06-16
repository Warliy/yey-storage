// SyncanyExt.cpp : Implementation of DLL Exports.

// REF: lp:tortoisebzr/shellext/syncanyshellext.cpp
// This file largely copied/modified to suit purposes from the TortoiseBzr project. Thank you!


#include "stdafx.h"
#include "resource.h"
#include "SyncanyExt_i.h"
#include "dllmain.h"
#include "ContextMenuExt.h"
#include "ShellIconExt.h"

#include <new>
#include <string>

using namespace std;

inline static std::string Guid2String(const GUID &g) {
    wchar_t wbuf[64];
    char buf[64];
    ::StringFromGUID2(g, wbuf, 64);
    for (int i = 0; i < 64; ++i) {
        buf[i] = (char)wbuf[i];
    }
    return std::string(buf);
}

static bool perUserRegistration = false;
HMODULE g_hModule;
LONG g_numObject;
BOOL g_isExplorer;

/// Class Factory. This class is static singleton.
template<typename T>
class SyncanyClassFactory: public IClassFactory
{
    LONG m_refcnt;
public:

    SyncanyClassFactory() : m_refcnt(0) {}

    STDMETHODIMP QueryInterface(REFIID riid, LPVOID FAR *ppv) {
        if (!ppv)
            return E_POINTER;
        *ppv = NULL;
        if (IsEqualIID(riid, IID_IUnknown) || IsEqualIID(riid, IID_IClassFactory)) {
            *ppv = (LPCLASSFACTORY)this;
            AddRef();
            return NOERROR;
        }
        return E_NOINTERFACE;
    }

    STDMETHODIMP_(ULONG) AddRef() {
        InterlockedIncrement(&g_numObject);
        return InterlockedIncrement(&m_refcnt);
    }
    STDMETHODIMP_(ULONG) Release() { // don't delete this.
        InterlockedDecrement(&g_numObject);
        return InterlockedDecrement(&m_refcnt);
    }

    STDMETHODIMP CreateInstance(LPUNKNOWN pUnkOuter, REFIID riid, LPVOID FAR* ppvObj) {
        if (pUnkOuter)
            return CLASS_E_NOAGGREGATION;

        T *obj = new T();
        HRESULT hr = obj->QueryInterface(riid, ppvObj);
        if (FAILED(hr))
            delete obj;
        return hr;
    }

    STDMETHODIMP LockServer(BOOL) {
        return NOERROR;
    }
};


// Used to determine whether the DLL can be unloaded by OLE.
STDAPI DllCanUnloadNow(void)
{
	AFX_MANAGE_STATE(AfxGetStaticModuleState());
	return g_numObject == 0 ? S_OK : S_FALSE;
}

// Returns a class factory to create an object of the requested type.
STDAPI DllGetClassObject(REFCLSID rclsid, REFIID riid, LPVOID* ppv)
{
    //static SyncanyClassFactory<CCopyHook> copyhookFactory;
    //static SyncanyClassFactory<CShellIconExt_Added> overlayAddedFactory;
    static SyncanyClassFactory<CShellIconExt_Normal> overlayNormalFactory;
    static SyncanyClassFactory<CShellIconExt_Modified> overlayModifiedFactory;
    //static SyncanyClassFactory<CShellIconExt_Conflict> overlayConflictFactory;
    //static SyncanyClassFactory<CShellIconExt_Deleted> overlayDeletedFactory;
    //static SyncanyClassFactory<CShellIconExt_Ignored> overlayIgnoredFactory;
    static SyncanyClassFactory<CShellIconExt_Unversioned> overlayUnversionedFactory;
    static SyncanyClassFactory<CShellIconExt_Unchanged> overlayUnchangedFactory;
    static SyncanyClassFactory<CContextMenuExt> contextmenuFactory;

	/*
    if (rclsid == CLSID_CopyHook)
        return copyhookFactory.QueryInterface(riid, ppv);
		*/
	/*
    if (rclsid == CLSID_IconOverlayAdded)
        return overlayAddedFactory.QueryInterface(riid, ppv);
		*/

    if (rclsid == CLSID_IconOverlayNormal)
        return overlayNormalFactory.QueryInterface(riid, ppv);

    if (rclsid == CLSID_IconOverlayModified)
        return overlayModifiedFactory.QueryInterface(riid, ppv);
	/*
    if (rclsid == CLSID_IconOverlayConflict)
        return overlayConflictFactory.QueryInterface(riid, ppv);
		*/
	/*
    if (rclsid == CLSID_IconOverlayDeleted)
        return overlayDeletedFactory.QueryInterface(riid, ppv);
		*/
	/*
    if (rclsid == CLSID_IconOverlayIgnored)
        return overlayIgnoredFactory.QueryInterface(riid, ppv);
		*/
    if (rclsid == CLSID_IconOverlayUnversioned)
        return overlayUnversionedFactory.QueryInterface(riid, ppv);

    if (rclsid == CLSID_ContextMenuExt)
        return contextmenuFactory.QueryInterface(riid, ppv);

    return CLASS_E_CLASSNOTAVAILABLE;
}

const GUID GUIDs[] = {
    //CLSID_CopyHook,
    //CLSID_IconOverlayAdded,
    CLSID_IconOverlayNormal,
    CLSID_IconOverlayModified,
    //CLSID_IconOverlayConflict,
    //CLSID_IconOverlayDeleted,
    //CLSID_IconOverlayIgnored,
    CLSID_IconOverlayUnversioned,
    CLSID_ContextMenuExt
};

#define APP_GUID "9B527650-9FC6-4082-B8D9-9CB63B34D65E"
const char APP_ID[] = "{" APP_GUID "}";
const char APP_NAME[] = "SyncanyExt";


void RegSetStringValue(HKEY root, const char *keyname, const char *valname, const char *val)
{
    HKEY hkey;
    long err;

    err = RegCreateKeyA(root, keyname, &hkey);
    if (err)
        throw std::exception("RegCreateKeyA failed.");

    err = RegSetValueExA(hkey, valname, 0, REG_SZ, (const BYTE*)val, (ULONG)strlen(val)+1);
    RegCloseKey(hkey);

    if (err)
        throw std::exception("RegSetValueExA failed.");
}

// DllRegisterServer - Adds entries to the system registry.
STDAPI DllRegisterServer(void)
{   
	OutputDebugStringA("Syncany: DllRegisterServer\n");

    char dllFileName[MAX_PATH+1];
    GetModuleFileNameA(_AtlBaseModule.GetModuleInstance(), dllFileName, MAX_PATH);

    static const char *descs[] = {
        //"Syncany CopyHook Shell Extension",
        //"Syncany IconOverlay Shell Extension: Added",
        "Syncany IconOverlay Shell Extension: Normal",
        "Syncany IconOverlay Shell Extension: Modified",
        //"Syncany IconOverlay Shell Extension: Conflict",
        //"Syncany IconOverlay Shell Extension: Deleted",
        //"Syncany IconOverlay Shell Extension: Ignored",
        "Syncany IconOverlay Shell Extension: Unversioned",
        "Syncany ContextMenu Shell Extension"
    };

    const int NUM_GUID = sizeof(GUIDs)/sizeof(GUIDs[0]);

	try{
        // Register class id.
        for (int i = 0; i < NUM_GUID; ++i) {
            std::string subkey = "CLSID\\";
            subkey += Guid2String(GUIDs[i]);

            RegSetStringValue(HKEY_CLASSES_ROOT, subkey.c_str(), 0, descs[i]);

            subkey += "\\InprocServer32";
            RegSetStringValue(HKEY_CLASSES_ROOT, subkey.c_str(), 0, dllFileName);
            RegSetStringValue(HKEY_CLASSES_ROOT, subkey.c_str(), "ThreadingModel", "Apartment");
        }

        // APP ID
        RegSetStringValue(HKEY_CLASSES_ROOT, "AppID\\{" APP_GUID "}", 0, APP_NAME);
        RegSetStringValue(HKEY_CLASSES_ROOT, "AppID\\SyncanyExt.dll", "AppID", APP_ID);

        // CopyHook
		/*
        RegSetStringValue(HKEY_CLASSES_ROOT, "Directory\\shellex\\CopyHookHandlers\\TortoiseBazaar",
                0, Guid2String(CLSID_CopyHook).c_str());
				*/

        //IconOverlay
        {
            HKEY root = perUserRegistration ? HKEY_CURRENT_USER : HKEY_LOCAL_MACHINE;

			/*
            RegSetStringValue(root, "Software\\TortoiseOverlays\\Added",
                    "Syncany", Guid2String(CLSID_IconOverlayAdded).c_str());
					*/

            RegSetStringValue(root, "Software\\TortoiseOverlays\\Normal",
                    "Syncany", Guid2String(CLSID_IconOverlayNormal).c_str());

            RegSetStringValue(root, "Software\\TortoiseOverlays\\Modified",
                    "Syncany", Guid2String(CLSID_IconOverlayModified).c_str());

			/*
            RegSetStringValue(root, "Software\\TortoiseOverlays\\Conflict",
                    "Syncany", Guid2String(CLSID_IconOverlayConflict).c_str());
					*/

			/*
            RegSetStringValue(root, "Software\\TortoiseOverlays\\Deleted",
                    "Syncany", Guid2String(CLSID_IconOverlayDeleted).c_str());
					*/

			/*
            RegSetStringValue(root, "Software\\TortoiseOverlays\\Ignored",
                    "Syncany", Guid2String(CLSID_IconOverlayIgnored).c_str());
					*/

            RegSetStringValue(root, "Software\\TortoiseOverlays\\Unversioned",
                    "Syncany", Guid2String(CLSID_IconOverlayUnversioned).c_str());
        }

        // ContextMenu
        {
            const string clsid = Guid2String(CLSID_ContextMenuExt).c_str();

            RegSetStringValue(HKEY_CLASSES_ROOT, "*\\shellex\\ContextMenuHandlers\\Syncany", 0, clsid.c_str());
            RegSetStringValue(HKEY_CLASSES_ROOT, "Directory\\Background\\shellex\\ContextMenuHandlers\\Syncany", 0, clsid.c_str());
            RegSetStringValue(HKEY_CLASSES_ROOT, "Directory\\shellex\\ContextMenuHandlers\\Syncany", 0, clsid.c_str());
            RegSetStringValue(HKEY_CLASSES_ROOT, "Folder\\shellex\\ContextMenuHandlers\\Syncany", 0, clsid.c_str());
        }

        // Approved list
        {
            RegSetStringValue(HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved",
                    Guid2String(CLSID_ContextMenuExt).c_str(), "Syncany Context Menu");
			/*
            RegSetStringValue(HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved",
                    Guid2String(CLSID_CopyHook).c_str(), "TortoiseBazaar Copy Hook");
			*/
            // We doesn't need to register icon overlay here. Because it is used through TortoiseOverlay.
        }
        return S_OK;
    }
    catch (std::exception e) {
        return E_FAIL;
    }
}

// DllUnregisterServer - Removes entries from the system registry.
STDAPI DllUnregisterServer(void)
{
    const int NUM_GUID = sizeof(GUIDs)/sizeof(GUIDs[0]);

    // class id.
    for (int i = 0; i < NUM_GUID; ++i) {
        string subkey = "CLSID\\";
        subkey += Guid2String(GUIDs[i]);
        string subkey2 = subkey + "\\InprocServer32";

        RegDeleteKeyA(HKEY_CLASSES_ROOT, subkey2.c_str());
        RegDeleteKeyA(HKEY_CLASSES_ROOT, subkey.c_str());
    }

    // APP ID
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "AppID\\{" APP_GUID "}");
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "AppID\\SyncanyExt.dll");

    // CopyHook
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "Directory\\shellex\\CopyHookHandlers\\Syncany");

    {   //IconOverlay
        HKEY root = perUserRegistration ? HKEY_CURRENT_USER : HKEY_LOCAL_MACHINE;

        const char *keys[] = {
            "Software\\TortoiseOverlays\\Added",
            "Software\\TortoiseOverlays\\Normal",
            "Software\\TortoiseOverlays\\Modified",
            "Software\\TortoiseOverlays\\Conflict",
            "Software\\TortoiseOverlays\\Deleted",
            "Software\\TortoiseOverlays\\Ignored",
            "Software\\TortoiseOverlays\\Unversioned",
        };

        const int NUM_KEY = sizeof(keys) / sizeof(keys[0]);

        for (int i = 0; i < NUM_KEY; ++i) {
            HKEY hkey;
            long err = RegCreateKeyA(root, keys[i], &hkey);
            if (err) {
                continue;
            }
            RegDeleteValueA(hkey, "Syncany");
            RegCloseKey(hkey);
        }
    }

    // ContextMenu
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "*\\shellex\\ContextMenuHandlers\\Syncany");
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "Directory\\Background\\shellex\\ContextMenuHandlers\\Syncany");
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "Directory\\shellex\\ContextMenuHandlers\\Syncany");
    RegDeleteKeyA(HKEY_CLASSES_ROOT, "Folder\\shellex\\ContextMenuHandlers\\Syncany");

    // Approved list
    {
        HKEY hkey_approved;
        long err = RegOpenKeyA(HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved",
                &hkey_approved);
        if (!err) {
            RegDeleteValueA(hkey_approved, Guid2String(CLSID_ContextMenuExt).c_str());
            //RegDeleteValueA(hkey_approved, Guid2String(CLSID_CopyHook).c_str());
            RegCloseKey(hkey_approved);
        }
    }
    return S_OK;
}

// DllInstall - Adds/Removes entries to the system registry per user per machine.
STDAPI DllInstall(BOOL bInstall, LPCWSTR pszCmdLine)
{
	HRESULT hr = E_FAIL;
	static const wchar_t szUserSwitch[] = L"user";

	if (pszCmdLine != NULL)
	{
		if (_wcsnicmp(pszCmdLine, szUserSwitch, _countof(szUserSwitch)) == 0)
		{
			ATL::AtlSetPerUserRegistration(true);
		}
	}

	if (bInstall)
	{	
		hr = DllRegisterServer();
		if (FAILED(hr))
		{
			DllUnregisterServer();
		}
	}
	else
	{
		hr = DllUnregisterServer();
	}

	return hr;
}