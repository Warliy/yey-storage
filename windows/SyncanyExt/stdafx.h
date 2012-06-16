// stdafx.h : include file for standard system include files,
// or project specific include files that are used frequently,
// but are changed infrequently

#pragma once

#ifndef STRICT
#define STRICT
#endif

#include "targetver.h"

#define _ATL_APARTMENT_THREADED

#define _ATL_NO_AUTOMATIC_NAMESPACE

#define _ATL_CSTRING_EXPLICIT_CONSTRUCTORS	// some CString constructors will be explicit

#include <afxwin.h>
#include <afxext.h>
#include <afxole.h>
#include <afxodlgs.h>
#include <afxrich.h>
#include <afxhtml.h>
#include <afxcview.h>
#include <afxwinappex.h>
#include <afxframewndex.h>
#include <afxmdiframewndex.h>

#ifndef _AFX_NO_OLE_SUPPORT
#include <afxdisp.h>        // MFC Automation classes
#endif // _AFX_NO_OLE_SUPPORT

#define ATL_NO_ASSERT_ON_DESTROY_NONEXISTENT_WINDOW

#include "resource.h"
#include <atlbase.h>
#include <atlcom.h>
#include <atlctl.h>

#include "INITGUID.h"

DEFINE_GUID(CLSID_IconOverlayNothing,0xD720C2C8,0x4EC5,0x4E78,0xAD,0x33,0xE7,0x92,0xA5,0xA6,0xF1,0x88);
DEFINE_GUID(CLSID_IconOverlayNormal,0xD720C2C9,0x4EC5,0x4E78,0xAD,0x33,0xE7,0x92,0xA5,0xA6,0xF1,0x88);
DEFINE_GUID(CLSID_IconOverlayModified,0xD720C2CA,0x4EC5,0x4E78,0xAD,0x33,0xE7,0x92,0xA5,0xA6,0xF1,0x88);
DEFINE_GUID(CLSID_IconOverlayUnversioned,0xD720C2CB,0x4EC5,0x4E78,0xAD,0x33,0xE7,0x92,0xA5,0xA6,0xF1,0x88);
