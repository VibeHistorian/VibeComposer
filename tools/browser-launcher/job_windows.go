package main

import (
	"fmt"
	"syscall"
	"unsafe"
)

// Windows closes this private handle on exit, terminating the broker's entire
// process tree even if the broker is killed before it can run cleanup code.
var lifetimeJob syscall.Handle

type jobBasicLimits struct {
	ProcessTime, JobTime                 int64
	Flags                                uint32
	MinimumWorkingSet, MaximumWorkingSet uintptr
	ActiveProcessLimit                   uint32
	Affinity                             uintptr
	PriorityClass, SchedulingClass       uint32
}
type jobLimits struct {
	Basic                                                      jobBasicLimits
	IO                                                         [6]uint64
	ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory uintptr
}

func containProcessTree(process syscall.Handle) error {
	kernel := syscall.NewLazyDLL("kernel32.dll")
	handle, _, err := kernel.NewProc("CreateJobObjectW").Call(0, 0)
	if handle == 0 {
		return fmt.Errorf("CreateJobObject: %w", err)
	}
	limits := jobLimits{}
	limits.Basic.Flags = 0x2000 // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE; no breakaway.
	ok, _, err := kernel.NewProc("SetInformationJobObject").Call(handle, 9, uintptr(unsafe.Pointer(&limits)), unsafe.Sizeof(limits))
	if ok == 0 {
		syscall.CloseHandle(syscall.Handle(handle))
		return fmt.Errorf("SetInformationJobObject: %w", err)
	}
	ok, _, err = kernel.NewProc("AssignProcessToJobObject").Call(handle, uintptr(process))
	if ok == 0 {
		syscall.CloseHandle(syscall.Handle(handle))
		return fmt.Errorf("AssignProcessToJobObject: %w", err)
	}
	lifetimeJob = syscall.Handle(handle)
	return nil
}
