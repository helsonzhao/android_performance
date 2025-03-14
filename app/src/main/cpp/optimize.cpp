#include <jni.h>
#include <android/log.h>
#include <dirent.h>
#include <string>

int getNumberOfCPUCores() {
    int cores = 0;
    DIR *dir;
    struct dirent *ent;
    if ((dir = opendir("/sys/devices/system/cpu/")) != NULL) {
        while ((ent = readdir(dir)) != NULL) {
            std::string path = ent->d_name;
            if (path.find("cpu") == 0) {
                bool isCore = true;
                for (int i = 3; i < path.length(); i++) {
                    if (path[i] < '0' || path[i] > '9') {
                        isCore = false;
                        break;
                    }
                }
                if (isCore) {
                    cores++;
                }
            }
        }
        closedir(dir);
    }
    return cores;
}

int getMaxFreqCPU() {
    int maxFreq = -1;
    for (int i = 0; i < getNumberOfCPUCores(); i++) {
        std::string filename = "/sys/devices/system/cpu/cpu" +
                               std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        FILE* cpuInfoMaxFreqFile = fopen(filename.c_str(), "r");
        if (cpuInfoMaxFreqFile != nullptr) {
            char line[256];
            if (fgets(line, sizeof(line), cpuInfoMaxFreqFile) != nullptr) {
                try {
                    int freqBound = std::stoi(line);
                    if (freqBound > maxFreq) maxFreq = freqBound;
                } catch (const std::exception& e) {

                }
            }
            fclose(cpuInfoMaxFreqFile);
        }
    }
    return maxFreq;
}
