// dllmain.cpp : Implementation of DllMain.

#include "stdafx.h"
#include "resource.h"
#include "SyncanyExt_i.h"
#include "dllmain.h"

CSyncanyExtModule _AtlModule;

class CSyncanyExtApp : public CWinApp
{
public:

// Overrides
	virtual BOOL InitInstance();
	virtual int ExitInstance();

	DECLARE_MESSAGE_MAP()
};

BEGIN_MESSAGE_MAP(CSyncanyExtApp, CWinApp)
END_MESSAGE_MAP()

CSyncanyExtApp theApp;

BOOL CSyncanyExtApp::InitInstance()
{
	return CWinApp::InitInstance();
}

int CSyncanyExtApp::ExitInstance()
{
	return CWinApp::ExitInstance();
}
