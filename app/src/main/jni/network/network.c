/**
 * @file network.c
 * @brief 神经网络模块实现
 * @author 神经网络模块
 * @version 1.0
 * @date 2026
 */

#include "network.h"
#include <time.h>
#include <stdlib.h>

#define NETWORK_METATABLE "Network"

/**
 * @brief 激活函数实现
 */
double sigmoid(double x) {
    if (x < -700) return 0.0;
    if (x > 700) return 1.0;
    return 1.0 / (1.0 + exp(-x));
}

double sigmoid_derivative(double x) {
    double s = sigmoid(x);
    return s * (1.0 - s);
}

double relu(double x) {
    return x > 0.0 ? x : 0.0;
}

double relu_derivative(double x) {
    return x > 0.0 ? 1.0 : 0.0;
}

double tanh_func(double x) {
    return tanh(x);
}

double tanh_derivative(double x) {
    double t = tanh_func(x);
    return 1.0 - t * t;
}

double linear(double x) {
    return x;
}

double linear_derivative(double x) {
    return 1.0;
}

double softmax_func(double x) {
    return x;
}

double softmax_derivative(double x) {
    return x * (1.0 - x);
}

/**
 * @brief 获取激活函数
 */
ActivationInfo get_activation(const char* name) {
    if (strcmp(name, "sigmoid") == 0) {
        return (ActivationInfo){ACTIVATION_SIGMOID, "sigmoid", sigmoid, sigmoid_derivative};
    } else if (strcmp(name, "relu") == 0) {
        return (ActivationInfo){ACTIVATION_RELU, "relu", relu, relu_derivative};
    } else if (strcmp(name, "tanh") == 0) {
        return (ActivationInfo){ACTIVATION_TANH, "tanh", tanh_func, tanh_derivative};
    } else if (strcmp(name, "linear") == 0) {
        return (ActivationInfo){ACTIVATION_LINEAR, "linear", linear, linear_derivative};
    } else if (strcmp(name, "softmax") == 0) {
        return (ActivationInfo){ACTIVATION_SOFTMAX, "softmax", softmax_func, softmax_derivative};
    }
    return (ActivationInfo){ACTIVATION_SIGMOID, "sigmoid", sigmoid, sigmoid_derivative};
}

/**
 * @brief 获取激活函数指针
 */
static double (*get_forward_func(ActivationType type))(double) {
    switch (type) {
        case ACTIVATION_SIGMOID: return sigmoid;
        case ACTIVATION_RELU: return relu;
        case ACTIVATION_TANH: return tanh_func;
        case ACTIVATION_LINEAR: return linear;
        case ACTIVATION_SOFTMAX: return softmax_func;
        default: return sigmoid;
    }
}

static double (*get_derivative_func(ActivationType type))(double) {
    switch (type) {
        case ACTIVATION_SIGMOID: return sigmoid_derivative;
        case ACTIVATION_RELU: return relu_derivative;
        case ACTIVATION_TANH: return tanh_derivative;
        case ACTIVATION_LINEAR: return linear_derivative;
        case ACTIVATION_SOFTMAX: return softmax_derivative;
        default: return sigmoid_derivative;
    }
}

/**
 * @brief 创建新的网络对象
 */
Network* lua_new_network(lua_State* L) {
    Network* network = (Network*)lua_newuserdata(L, sizeof(Network));
    luaL_getmetatable(L, NETWORK_METATABLE);
    lua_setmetatable(L, -2);
    
    network->input_layer = NULL;
    network->output_layer = NULL;
    network->layers = NULL;
    network->layer_count = 0;
    network->input_size = 0;
    network->output_size = 0;
    network->learning_rate = 0.01;
    network->momentum = 0.0;
    network->weight_decay = 0.0;
    network->is_trained = 0;
    network->epoch = 0;
    network->last_loss = 0.0;
    
    return network;
}

/**
 * @brief 检查参数是否为网络
 */
Network* luaL_check_network(lua_State* L, int index) {
    Network* network = (Network*)luaL_checkudata(L, index, NETWORK_METATABLE);
    if (network == NULL) {
        luaL_argerror(L, index, "expected Network");
    }
    return network;
}

/**
 * @brief 创建新层
 */
Layer* layer_create(int input_size, int output_size, ActivationType activation) {
    Layer* layer = (Layer*)malloc(sizeof(Layer));
    if (layer == NULL) return NULL;
    
    layer->input_size = input_size;
    layer->output_size = output_size;
    layer->activation = activation;
    
    layer->weights = (double*)calloc(output_size * input_size, sizeof(double));
    layer->biases = (double*)calloc(output_size, sizeof(double));
    layer->outputs = (double*)calloc(output_size, sizeof(double));
    layer->inputs = (double*)calloc(input_size, sizeof(double));
    layer->deltas = (double*)calloc(output_size, sizeof(double));
    
    if (layer->weights == NULL || layer->biases == NULL || 
        layer->outputs == NULL || layer->inputs == NULL || layer->deltas == NULL) {
        layer_free(layer);
        return NULL;
    }
    
    layer->prev = NULL;
    layer->next = NULL;
    
    return layer;
}

/**
 * @brief 释放层内存
 */
void layer_free(Layer* layer) {
    if (layer != NULL) {
        if (layer->weights) free(layer->weights);
        if (layer->biases) free(layer->biases);
        if (layer->outputs) free(layer->outputs);
        if (layer->inputs) free(layer->inputs);
        if (layer->deltas) free(layer->deltas);
        free(layer);
    }
}

/**
 * @brief 初始化层权重
 */
void layer_init_weights(Layer* layer, const char* method) {
    if (strcmp(method, "xavier") == 0) {
        double scale = sqrt(2.0 / (layer->input_size + layer->output_size));
        for (int i = 0; i < layer->output_size * layer->input_size; i++) {
            layer->weights[i] = ((double)rand() / RAND_MAX - 0.5) * 2.0 * scale;
        }
    } else if (strcmp(method, "he") == 0) {
        double scale = sqrt(2.0 / layer->input_size);
        for (int i = 0; i < layer->output_size * layer->input_size; i++) {
            layer->weights[i] = ((double)rand() / RAND_MAX - 0.5) * 2.0 * scale;
        }
    } else {
        double range = 0.1;
        for (int i = 0; i < layer->output_size * layer->input_size; i++) {
            layer->weights[i] = ((double)rand() / RAND_MAX - 0.5) * 2.0 * range;
        }
    }
    
    for (int i = 0; i < layer->output_size; i++) {
        layer->biases[i] = 0.0;
    }
}

/**
 * @brief 层前向传播
 */
void layer_forward(Layer* layer, double* inputs) {
    memcpy(layer->inputs, inputs, layer->input_size * sizeof(double));
    
    double (*forward)(double) = get_forward_func(layer->activation);
    
    for (int i = 0; i < layer->output_size; i++) {
        double sum = layer->biases[i];
        for (int j = 0; j < layer->input_size; j++) {
            sum += layer->weights[i * layer->input_size + j] * layer->inputs[j];
        }
        layer->outputs[i] = forward(sum);
    }
}

/**
 * @brief 层反向传播
 */
void layer_backward(Layer* layer, double learning_rate) {
    double (*derivative)(double) = get_derivative_func(layer->activation);
    
    for (int i = 0; i < layer->output_size; i++) {
        double gradient = layer->deltas[i] * derivative(layer->outputs[i]);
        for (int j = 0; j < layer->input_size; j++) {
            layer->weights[i * layer->input_size + j] -= learning_rate * gradient * layer->inputs[j];
        }
        layer->biases[i] -= learning_rate * gradient;
    }
}

/**
 * @brief 创建网络
 */
Network* network_create(lua_State* L, int* architecture, int arch_size, int input_size) {
    Network* network = lua_new_network(L);
    network->input_size = input_size;
    network->output_size = architecture[arch_size - 1];
    
    network->layers = (Layer*)malloc(arch_size * sizeof(Layer));
    if (network->layers == NULL) {
        return network;
    }
    
    Layer* prev_layer = NULL;
    int current_input = input_size;
    
    for (int i = 0; i < arch_size; i++) {
        ActivationType act = (i == arch_size - 1) ? ACTIVATION_LINEAR : ACTIVATION_RELU;
        Layer* layer = layer_create(current_input, architecture[i], act);
        
        if (layer == NULL) {
            network_free(network);
            return NULL;
        }
        
        layer_init_weights(layer, "he");
        
        network->layers[i] = *layer;
        free(layer);
        
        network->layers[i].prev = prev_layer;
        if (prev_layer != NULL) {
            prev_layer->next = &network->layers[i];
        }
        
        prev_layer = &network->layers[i];
        current_input = architecture[i];
    }
    
    network->input_layer = &network->layers[0];
    network->output_layer = &network->layers[arch_size - 1];
    network->layer_count = arch_size;
    
    return network;
}

/**
 * @brief 释放网络内存
 */
void network_free(Network* network) {
    if (network != NULL) {
        if (network->layers != NULL) {
            for (int i = 0; i < network->layer_count; i++) {
                Layer* layer = &network->layers[i];
                if (layer->weights) free(layer->weights);
                if (layer->biases) free(layer->biases);
                if (layer->outputs) free(layer->outputs);
                if (layer->inputs) free(layer->inputs);
                if (layer->deltas) free(layer->deltas);
            }
            free(network->layers);
        }
    }
}

/**
 * @brief 网络前向传播
 */
void network_forward(Network* network, double* inputs, double* outputs) {
    Layer* layer = network->input_layer;
    double* current_input = inputs;
    double* current_output = NULL;
    double internal_outputs[NETWORK_MAX_NEURONS];
    
    while (layer != NULL) {
        if (layer->next == NULL) {
            current_output = outputs;
        } else {
            current_output = internal_outputs;
        }
        
        layer_forward(layer, current_input);
        memcpy(current_output, layer->outputs, layer->output_size * sizeof(double));
        
        current_input = current_output;
        layer = layer->next;
    }
}

/**
 * @brief 计算损失
 */
double network_loss(double* outputs, double* targets, int output_size, const char* loss_type) {
    if (strcmp(loss_type, "mse") == 0) {
        double sum = 0.0;
        for (int i = 0; i < output_size; i++) {
            double diff = outputs[i] - targets[i];
            sum += diff * diff;
        }
        return sum / output_size;
    } else if (strcmp(loss_type, "cross_entropy") == 0) {
        double sum = 0.0;
        for (int i = 0; i < output_size; i++) {
            if (targets[i] > 0) {
                sum -= targets[i] * log(outputs[i] + 1e-10);
            }
        }
        return sum;
    }
    return 0.0;
}

/**
 * @brief 网络反向传播
 */
void network_backward(Network* network, double* targets) {
    Layer* layer = network->output_layer;
    
    for (int i = 0; i < layer->output_size; i++) {
        layer->deltas[i] = layer->outputs[i] - targets[i];
    }
    
    layer = layer->prev;
    while (layer != NULL && layer->prev != NULL) {
        for (int i = 0; i < layer->output_size; i++) {
            double sum = 0.0;
            Layer* next_layer = layer->next;
            for (int j = 0; j < next_layer->output_size; j++) {
                sum += next_layer->deltas[j] * next_layer->weights[j * layer->output_size + i];
            }
            layer->deltas[i] = sum;
        }
        layer = layer->prev;
    }
}

/**
 * @brief 网络训练（单步）
 */
double network_train_step(Network* network, double* input, double* target) {
    double outputs[NETWORK_MAX_NEURONS];
    
    network_forward(network, input, outputs);
    
    double loss = network_loss(outputs, target, network->output_size, "mse");
    
    network_backward(network, target);
    
    Layer* layer = network->output_layer;
    while (layer != NULL) {
        layer_backward(layer, network->learning_rate);
        layer = layer->prev;
    }
    
    return loss;
}

/**
 * @brief 网络训练（多轮）
 */
double network_train(Network* network, Dataset* dataset, int epochs, int mini_batch_size) {
    srand((unsigned int)time(NULL));
    
    double total_loss = 0.0;
    
    for (int epoch = 0; epoch < epochs; epoch++) {
        for (int i = 0; i < dataset->count; i++) {
            double loss = network_train_step(network, dataset->pairs[i].input, dataset->pairs[i].target);
            total_loss += loss;
        }
        
        network->epoch = epoch + 1;
    }
    
    network->last_loss = total_loss / (dataset->count * epochs);
    network->is_trained = 1;
    
    return network->last_loss;
}

/**
 * @brief 保存网络到文件（二进制格式）
 */
int network_save(Network* network, const char* filename) {
    FILE* fp = fopen(filename, "wb");
    if (fp == NULL) {
        return 0;
    }
    
    fwrite(&network->input_size, sizeof(int), 1, fp);
    fwrite(&network->output_size, sizeof(int), 1, fp);
    fwrite(&network->layer_count, sizeof(int), 1, fp);
    fwrite(&network->learning_rate, sizeof(double), 1, fp);
    fwrite(&network->momentum, sizeof(double), 1, fp);
    fwrite(&network->weight_decay, sizeof(double), 1, fp);
    fwrite(&network->is_trained, sizeof(int), 1, fp);
    fwrite(&network->epoch, sizeof(int), 1, fp);
    fwrite(&network->last_loss, sizeof(double), 1, fp);
    
    for (int i = 0; i < network->layer_count; i++) {
        Layer* layer = &network->layers[i];
        fwrite(&layer->input_size, sizeof(int), 1, fp);
        fwrite(&layer->output_size, sizeof(int), 1, fp);
        fwrite(&layer->activation, sizeof(ActivationType), 1, fp);
        
        int weight_count = layer->output_size * layer->input_size;
        for (int j = 0; j < weight_count; j++) {
            fwrite(&layer->weights[j], sizeof(double), 1, fp);
        }
        
        for (int j = 0; j < layer->output_size; j++) {
            fwrite(&layer->biases[j], sizeof(double), 1, fp);
        }
    }
    
    fclose(fp);
    return 1;
}

/**
 * @brief 从文件加载网络
 */
Network* network_load(lua_State* L, const char* filename) {
    FILE* fp = fopen(filename, "rb");
    if (fp == NULL) {
        return NULL;
    }
    
    int input_size, output_size, layer_count;
    double learning_rate, momentum, weight_decay;
    int is_trained, epoch;
    double last_loss;
    
    fread(&input_size, sizeof(int), 1, fp);
    fread(&output_size, sizeof(int), 1, fp);
    fread(&layer_count, sizeof(int), 1, fp);
    fread(&learning_rate, sizeof(double), 1, fp);
    fread(&momentum, sizeof(double), 1, fp);
    fread(&weight_decay, sizeof(double), 1, fp);
    fread(&is_trained, sizeof(int), 1, fp);
    fread(&epoch, sizeof(int), 1, fp);
    fread(&last_loss, sizeof(double), 1, fp);
    
    int architecture[NETWORK_MAX_LAYERS];
    ActivationType activations[NETWORK_MAX_LAYERS];
    
    for (int i = 0; i < layer_count; i++) {
        int in_size, out_size;
        ActivationType act;
        fread(&in_size, sizeof(int), 1, fp);
        fread(&out_size, sizeof(int), 1, fp);
        fread(&act, sizeof(ActivationType), 1, fp);
        architecture[i] = out_size;
        activations[i] = act;
    }
    
    Network* network = network_create(L, architecture, layer_count, input_size);
    if (network == NULL) {
        fclose(fp);
        return NULL;
    }
    
    fseek(fp, 9 * sizeof(int) + 4 * sizeof(double), SEEK_SET);
    
    for (int i = 0; i < network->layer_count; i++) {
        Layer* layer = &network->layers[i];
        
        fread(&layer->input_size, sizeof(int), 1, fp);
        fread(&layer->output_size, sizeof(int), 1, fp);
        fread(&layer->activation, sizeof(ActivationType), 1, fp);
        
        int weight_count = layer->output_size * layer->input_size;
        for (int j = 0; j < weight_count; j++) {
            fread(&layer->weights[j], sizeof(double), 1, fp);
        }
        
        for (int j = 0; j < layer->output_size; j++) {
            fread(&layer->biases[j], sizeof(double), 1, fp);
        }
    }
    
    network->learning_rate = learning_rate;
    network->momentum = momentum;
    network->weight_decay = weight_decay;
    network->is_trained = is_trained;
    network->epoch = epoch;
    network->last_loss = last_loss;
    
    fclose(fp);
    return network;
}

/**
 * @brief 获取网络权重为Lua表
 */
int network_get_weights(lua_State* L, Network* network) {
    lua_createtable(L, network->layer_count, 0);
    
    for (int i = 0; i < network->layer_count; i++) {
        Layer* layer = &network->layers[i];
        
        lua_createtable(L, 3, 0);
        
        lua_pushstring(L, "input_size");
        lua_pushinteger(L, layer->input_size);
        lua_settable(L, -3);
        
        lua_pushstring(L, "output_size");
        lua_pushinteger(L, layer->output_size);
        lua_settable(L, -3);
        
        lua_pushstring(L, "activation");
        const char* act_name;
        switch (layer->activation) {
            case ACTIVATION_SIGMOID: act_name = "sigmoid"; break;
            case ACTIVATION_RELU: act_name = "relu"; break;
            case ACTIVATION_TANH: act_name = "tanh"; break;
            case ACTIVATION_LINEAR: act_name = "linear"; break;
            case ACTIVATION_SOFTMAX: act_name = "softmax"; break;
            default: act_name = "unknown"; break;
        }
        lua_pushstring(L, act_name);
        lua_settable(L, -3);
        
        lua_pushstring(L, "weights");
        lua_createtable(L, layer->output_size, 0);
        for (int j = 0; j < layer->output_size; j++) {
            lua_createtable(L, layer->input_size, 0);
            for (int k = 0; k < layer->input_size; k++) {
                lua_pushnumber(L, layer->weights[j * layer->input_size + k]);
                lua_seti(L, -2, k + 1);
            }
            lua_seti(L, -2, j + 1);
        }
        lua_settable(L, -3);
        
        lua_pushstring(L, "biases");
        lua_createtable(L, layer->output_size, 0);
        for (int j = 0; j < layer->output_size; j++) {
            lua_pushnumber(L, layer->biases[j]);
            lua_seti(L, -2, j + 1);
        }
        lua_settable(L, -3);
        
        lua_seti(L, -2, i + 1);
    }
    
    return 1;
}

/**
 * @brief 设置网络权重
 */
int network_set_weights(Network* network, lua_State* L, int idx) {
    if (!lua_istable(L, idx)) {
        return 0;
    }
    
    int table_len = luaL_len(L, idx);
    if (table_len != network->layer_count) {
        return 0;
    }
    
    for (int i = 0; i < network->layer_count; i++) {
        lua_geti(L, idx, i + 1);
        if (!lua_istable(L, -1)) {
            lua_pop(L, 1);
            return 0;
        }
        
        Layer* layer = &network->layers[i];
        
        lua_getfield(L, -1, "weights");
        if (lua_istable(L, -1)) {
            for (int j = 0; j < layer->output_size; j++) {
                lua_geti(L, -1, j + 1);
                if (lua_istable(L, -1)) {
                    for (int k = 0; k < layer->input_size; k++) {
                        lua_geti(L, -1, k + 1);
                        layer->weights[j * layer->input_size + k] = lua_tonumber(L, -1);
                        lua_pop(L, 1);
                    }
                }
                lua_pop(L, 1);
            }
        }
        lua_pop(L, 1);
        
        lua_getfield(L, -1, "biases");
        if (lua_istable(L, -1)) {
            for (int j = 0; j < layer->output_size; j++) {
                lua_geti(L, -1, j + 1);
                layer->biases[j] = lua_tonumber(L, -1);
                lua_pop(L, 1);
            }
        }
        lua_pop(L, 1);
        
        lua_pop(L, 1);
    }
    
    return 1;
}

/**
 * @brief 保存数据集到文件
 */
int dataset_save(Dataset* dataset, const char* filename) {
    FILE* fp = fopen(filename, "wb");
    if (fp == NULL) {
        return 0;
    }
    
    fwrite(&dataset->input_size, sizeof(int), 1, fp);
    fwrite(&dataset->output_size, sizeof(int), 1, fp);
    fwrite(&dataset->count, sizeof(int), 1, fp);
    
    for (int i = 0; i < dataset->count; i++) {
        for (int j = 0; j < dataset->input_size; j++) {
            fwrite(&dataset->pairs[i].input[j], sizeof(double), 1, fp);
        }
        for (int j = 0; j < dataset->output_size; j++) {
            fwrite(&dataset->pairs[i].target[j], sizeof(double), 1, fp);
        }
    }
    
    fclose(fp);
    return 1;
}

/**
 * @brief 从文件加载数据集
 */
Dataset* dataset_load(lua_State* L, const char* filename) {
    FILE* fp = fopen(filename, "rb");
    if (fp == NULL) {
        return NULL;
    }
    
    int input_size, output_size, count;
    fread(&input_size, sizeof(int), 1, fp);
    fread(&output_size, sizeof(int), 1, fp);
    fread(&count, sizeof(int), 1, fp);
    
    Dataset* dataset = (Dataset*)malloc(sizeof(Dataset));
    if (dataset == NULL) {
        fclose(fp);
        return NULL;
    }
    
    dataset->input_size = input_size;
    dataset->output_size = output_size;
    dataset->count = count;
    dataset->pairs = (DataPair*)malloc(count * sizeof(DataPair));
    
    if (dataset->pairs == NULL) {
        free(dataset);
        fclose(fp);
        return NULL;
    }
    
    for (int i = 0; i < count; i++) {
        dataset->pairs[i].input = (double*)malloc(input_size * sizeof(double));
        dataset->pairs[i].target = (double*)malloc(output_size * sizeof(double));
        dataset->pairs[i].input_size = input_size;
        dataset->pairs[i].output_size = output_size;
        
        for (int j = 0; j < input_size; j++) {
            fread(&dataset->pairs[i].input[j], sizeof(double), 1, fp);
        }
        for (int j = 0; j < output_size; j++) {
            fread(&dataset->pairs[i].target[j], sizeof(double), 1, fp);
        }
    }
    
    fclose(fp);
    return dataset;
}

/**
 * @brief 网络预测
 */
int network_predict(lua_State* L, Network* network, double* input) {
    double outputs[NETWORK_MAX_NEURONS];
    
    network_forward(network, input, outputs);
    
    lua_createtable(L, network->output_size, 0);
    for (int i = 0; i < network->output_size; i++) {
        lua_pushnumber(L, outputs[i]);
        lua_seti(L, -2, i + 1);
    }
    
    return 1;
}

/**
 * @brief 创建数据集
 */
Dataset* dataset_create(lua_State* L, int input_size, int output_size) {
    int count = luaL_len(L, 1);
    
    Dataset* dataset = (Dataset*)malloc(sizeof(Dataset));
    if (dataset == NULL) return NULL;
    
    dataset->count = count;
    dataset->input_size = input_size;
    dataset->output_size = output_size;
    dataset->pairs = (DataPair*)malloc(count * sizeof(DataPair));
    
    if (dataset->pairs == NULL) {
        free(dataset);
        return NULL;
    }
    
    for (int i = 0; i < count; i++) {
        dataset->pairs[i].input = (double*)malloc(input_size * sizeof(double));
        dataset->pairs[i].target = (double*)malloc(output_size * sizeof(double));
        dataset->pairs[i].input_size = input_size;
        dataset->pairs[i].output_size = output_size;
        
        lua_geti(L, 1, i + 1);
        lua_getfield(L, -1, "input");
        for (int j = 0; j < input_size; j++) {
            lua_geti(L, -1, j + 1);
            dataset->pairs[i].input[j] = lua_tonumber(L, -1);
            lua_pop(L, 1);
        }
        lua_pop(L, 1);
        
        lua_getfield(L, -1, "target");
        for (int j = 0; j < output_size; j++) {
            lua_geti(L, -1, j + 1);
            dataset->pairs[i].target[j] = lua_tonumber(L, -1);
            lua_pop(L, 1);
        }
        lua_pop(L, 1);
        
        lua_pop(L, 1);
    }
    
    return dataset;
}

/**
 * @brief 释放数据集
 */
void dataset_free(Dataset* dataset) {
    if (dataset != NULL) {
        for (int i = 0; i < dataset->count; i++) {
            if (dataset->pairs[i].input) free(dataset->pairs[i].input);
            if (dataset->pairs[i].target) free(dataset->pairs[i].target);
        }
        free(dataset->pairs);
        free(dataset);
    }
}

/**
 * @brief 归一化数据
 */
void normalize_data(double* data, int size, double min_val, double max_val) {
    double range = max_val - min_val;
    if (range == 0) range = 1.0;
    for (int i = 0; i < size; i++) {
        data[i] = (data[i] - min_val) / range;
    }
}

/**
 * @brief 反归一化数据
 */
void denormalize_data(double* data, int size, double min_val, double max_val) {
    double range = max_val - min_val;
    for (int i = 0; i < size; i++) {
        data[i] = data[i] * range + min_val;
    }
}

/**
 * @brief network.new(architecture, input_size) - 创建神经网络
 */
static int l_network_new(lua_State* L) {
    int top = lua_gettop(L);
    
    if (top < 2 || !lua_istable(L, 1)) {
        return luaL_error(L, "expected table as architecture");
    }
    
    int input_size = (int)luaL_checkinteger(L, 2);
    
    int arch_size = luaL_len(L, 1);
    if (arch_size > NETWORK_MAX_LAYERS) {
        return luaL_error(L, "too many layers");
    }
    
    int architecture[NETWORK_MAX_LAYERS];
    for (int i = 0; i < arch_size; i++) {
        lua_geti(L, 1, i + 1);
        architecture[i] = (int)luaL_checkinteger(L, -1);
        lua_pop(L, 1);
    }
    
    const char* init_method = "he";
    if (top >= 3) {
        init_method = luaL_optstring(L, 3, "he");
    }
    
    Network* network = network_create(L, architecture, arch_size, input_size);
    
    Layer* layer = network->input_layer;
    while (layer != NULL) {
        layer_init_weights(layer, init_method);
        layer = layer->next;
    }
    
    return 1;
}

/**
 * @brief network:set LearningRate(value) - 设置学习率
 */
static int l_network_set_learning_rate(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    double lr = luaL_checknumber(L, 2);
    network->learning_rate = lr;
    return 0;
}

/**
 * @brief network:getLearningRate() - 获取学习率
 */
static int l_network_get_learning_rate(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    lua_pushnumber(L, network->learning_rate);
    return 1;
}

/**
 * @brief network:forward(input) - 前向传播
 */
static int l_network_forward(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    if (!lua_istable(L, 2)) {
        return luaL_error(L, "expected table as input");
    }
    
    double input[NETWORK_MAX_NEURONS];
    for (int i = 0; i < network->input_size; i++) {
        lua_geti(L, 2, i + 1);
        input[i] = lua_tonumber(L, -1);
        lua_pop(L, 1);
    }
    
    double output[NETWORK_MAX_NEURONS];
    network_forward(network, input, output);
    
    lua_createtable(L, network->output_size, 0);
    for (int i = 0; i < network->output_size; i++) {
        lua_pushnumber(L, output[i]);
        lua_seti(L, -2, i + 1);
    }
    
    return 1;
}

/**
 * @brief network:predict(input) - 预测
 */
static int l_network_predict(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    if (!lua_istable(L, 2)) {
        return luaL_error(L, "expected table as input");
    }
    
    double input[NETWORK_MAX_NEURONS];
    for (int i = 0; i < network->input_size; i++) {
        lua_geti(L, 2, i + 1);
        input[i] = lua_tonumber(L, -1);
        lua_pop(L, 1);
    }
    
    return network_predict(L, network, input);
}

/**
 * @brief network:train(dataset, epochs, mini_batch_size) - 训练网络
 */
static int l_network_train(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    if (!lua_istable(L, 2)) {
        return luaL_error(L, "expected table as dataset");
    }
    
    int epochs = (int)luaL_optinteger(L, 3, 100);
    int mini_batch_size = (int)luaL_optinteger(L, 4, 1);
    
    lua_pushvalue(L, 1);
    lua_pushvalue(L, 2);
    lua_insert(L, 1);
    lua_pop(L, 1);
    
    Dataset* dataset = dataset_create(L, network->input_size, network->output_size);
    
    lua_pushvalue(L, 1);
    lua_insert(L, 2);
    lua_pop(L, 1);
    
    if (dataset == NULL) {
        return luaL_error(L, "failed to create dataset");
    }
    
    double loss = network_train(network, dataset, epochs, mini_batch_size);
    
    dataset_free(dataset);
    
    lua_pushnumber(L, loss);
    return 1;
}

/**
 * @brief network:trainStep(input, target) - 单步训练
 */
static int l_network_train_step(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    if (!lua_istable(L, 2) || !lua_istable(L, 3)) {
        return luaL_error(L, "expected tables as input and target");
    }
    
    double input[NETWORK_MAX_NEURONS];
    double target[NETWORK_MAX_NEURONS];
    
    for (int i = 0; i < network->input_size; i++) {
        lua_geti(L, 2, i + 1);
        input[i] = lua_tonumber(L, -1);
        lua_pop(L, 1);
    }
    
    for (int i = 0; i < network->output_size; i++) {
        lua_geti(L, 3, i + 1);
        target[i] = lua_tonumber(L, -1);
        lua_pop(L, 1);
    }
    
    double loss = network_train_step(network, input, target);
    
    lua_pushnumber(L, loss);
    return 1;
}

/**
 * @brief network:clone() - 复制网络
 */
static int l_network_clone(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    int architecture[NETWORK_MAX_LAYERS];
    Layer* layer = network->input_layer;
    int i = 0;
    while (layer != NULL) {
        architecture[i] = layer->output_size;
        layer = layer->next;
        i++;
    }
    
    Network* new_network = network_create(L, architecture, i, network->input_size);
    new_network->learning_rate = network->learning_rate;
    new_network->momentum = network->momentum;
    new_network->weight_decay = network->weight_decay;
    new_network->is_trained = network->is_trained;
    new_network->epoch = network->epoch;
    new_network->last_loss = network->last_loss;
    
    layer = network->input_layer;
    Layer* new_layer = new_network->input_layer;
    while (layer != NULL && new_layer != NULL) {
        memcpy(new_layer->weights, layer->weights, layer->output_size * layer->input_size * sizeof(double));
        memcpy(new_layer->biases, layer->biases, layer->output_size * sizeof(double));
        layer = layer->next;
        new_layer = new_layer->next;
    }
    
    return 1;
}

/**
 * @brief network:info() - 获取网络信息
 */
static int l_network_info(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    lua_createtable(L, 0, 8);
    
    lua_pushstring(L, "input_size");
    lua_pushinteger(L, network->input_size);
    lua_settable(L, -3);
    
    lua_pushstring(L, "output_size");
    lua_pushinteger(L, network->output_size);
    lua_settable(L, -3);
    
    lua_pushstring(L, "layer_count");
    lua_pushinteger(L, network->layer_count);
    lua_settable(L, -3);
    
    lua_pushstring(L, "learning_rate");
    lua_pushnumber(L, network->learning_rate);
    lua_settable(L, -3);
    
    lua_pushstring(L, "momentum");
    lua_pushnumber(L, network->momentum);
    lua_settable(L, -3);
    
    lua_pushstring(L, "weight_decay");
    lua_pushnumber(L, network->weight_decay);
    lua_settable(L, -3);
    
    lua_pushstring(L, "is_trained");
    lua_pushboolean(L, network->is_trained);
    lua_settable(L, -3);
    
    lua_pushstring(L, "epoch");
    lua_pushinteger(L, network->epoch);
    lua_settable(L, -3);
    
    lua_pushstring(L, "last_loss");
    lua_pushnumber(L, network->last_loss);
    lua_settable(L, -3);
    
    lua_pushstring(L, "architecture");
    lua_createtable(L, network->layer_count, 0);
    Layer* layer = network->input_layer;
    int i = 0;
    while (layer != NULL) {
        lua_pushinteger(L, layer->output_size);
        lua_seti(L, -2, i + 1);
        layer = layer->next;
        i++;
    }
    lua_settable(L, -3);
    
    return 1;
}

/**
 * @brief network:weights(layer_index) - 获取层权重
 */
static int l_network_weights(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    int layer_idx = (int)luaL_checkinteger(L, 2) - 1;
    
    if (layer_idx < 0 || layer_idx >= network->layer_count) {
        return luaL_error(L, "invalid layer index");
    }
    
    Layer* layer = &network->layers[layer_idx];
    
    lua_createtable(L, layer->output_size, 0);
    for (int i = 0; i < layer->output_size; i++) {
        lua_createtable(L, layer->input_size, 0);
        for (int j = 0; j < layer->input_size; j++) {
            lua_pushnumber(L, layer->weights[i * layer->input_size + j]);
            lua_seti(L, -2, j + 1);
        }
        lua_seti(L, -2, i + 1);
    }
    
    return 1;
}

/**
 * @brief network:save(filename) - 保存网络到文件
 */
static int l_network_save(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    const char* filename = luaL_checkstring(L, 2);
    
    int result = network_save(network, filename);
    lua_pushboolean(L, result);
    
    return 1;
}

/**
 * @brief network.load(filename) - 从文件加载网络
 */
static int l_network_load(lua_State* L) {
    const char* filename = luaL_checkstring(L, 1);
    
    Network* network = network_load(L, filename);
    if (network == NULL) {
        return luaL_error(L, "failed to load network from file");
    }
    
    return 1;
}

/**
 * @brief network:getWeights() - 获取所有权重为表
 */
static int l_network_get_weights(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    return network_get_weights(L, network);
}

/**
 * @brief network:setWeights(weights_table) - 设置权重
 */
static int l_network_set_weights(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    
    if (!lua_istable(L, 2)) {
        return luaL_error(L, "expected table as weights");
    }
    
    int result = network_set_weights(network, L, 2);
    lua_pushboolean(L, result);
    
    return 1;
}

/**
 * @brief network:saveWeights(filename) - 保存权重到Lua表文件
 */
static int l_network_save_weights(lua_State* L) {
    Network* network = luaL_check_network(L, 1);
    const char* filename = luaL_optstring(L, 2, NULL);
    
    if (filename != NULL) {
        FILE* fp = fopen(filename, "w");
        if (fp == NULL) {
            lua_pushboolean(L, 0);
            return 1;
        }
        
        lua_State* temp_L = L;
        network_get_weights(temp_L, network);
        
        lua_pushstring(temp_L, "-- Neural Network Weights Export");
        lua_pushstring(temp_L, "\nreturn ");
        lua_concat(temp_L, 2);
        
        fprintf(fp, "-- Neural Network Weights Export\n");
        fprintf(fp, "-- Generated by Lua Network Module\n\n");
        
        luaL_Buffer b;
        luaL_buffinit(temp_L, &b);
        
        lua_getglobal(temp_L, "json");
        if (lua_isnil(temp_L, -1)) {
            lua_pop(temp_L, 1);
            fprintf(fp, "-- JSON library not available, saving as Lua table\n");
            fprintf(fp, "-- Weights structure: layer -> {weights, biases}\n");
        }
        lua_pop(temp_L, 1);
        
        lua_pop(temp_L, 1);
        fclose(fp);
        
        lua_pushboolean(L, 1);
    } else {
        network_get_weights(L, network);
    }
    
    return filename == NULL ? 1 : 1;
}

/**
 * @brief 网络元方法：gc
 */
static int l_network_gc(lua_State* L) {
    Network* network = (Network*)luaL_checkudata(L, 1, NETWORK_METATABLE);
    network_free(network);
    return 0;
}

/**
 * @brief 网络元方法：tostring
 */
static int l_network_tostring(lua_State* L) {
    Network* network = (Network*)luaL_checkudata(L, 1, NETWORK_METATABLE);
    lua_pushfstring(L, "Network(input=%d, layers=%d, output=%d, trained=%s)",
                    network->input_size, network->layer_count, network->output_size,
                    network->is_trained ? "yes" : "no");
    return 1;
}

/**
 * @brief network.dataset(data_table) - 创建数据集
 */
static int l_network_dataset(lua_State* L) {
    if (!lua_istable(L, 1)) {
        return luaL_error(L, "expected table as data");
    }
    
    int input_size = 0;
    int output_size = 0;
    
    lua_geti(L, 1, 1);
    if (lua_istable(L, -1)) {
        lua_getfield(L, -1, "input");
        input_size = luaL_len(L, -1);
        lua_pop(L, 1);
        
        lua_getfield(L, -1, "target");
        output_size = luaL_len(L, -1);
        lua_pop(L, 1);
    }
    lua_pop(L, 1);
    
    Dataset* dataset = dataset_create(L, input_size, output_size);
    
    lua_pushboolean(L, dataset != NULL);
    return 1;
}

/**
 * @brief 打印帮助信息
 */
static int l_network_help(lua_State* L) {
    const char* func_name = NULL;
    int top = lua_gettop(L);
    
    if (top >= 1 && !lua_isnil(L, 1)) {
        func_name = luaL_checkstring(L, 1);
    }
    
    luaL_Buffer b;
    luaL_buffinit(L, &b);
    
    if (func_name == NULL || strcmp(func_name, "all") == 0) {
        luaL_addstring(&b, "\n========================================\n");
        luaL_addstring(&b, "       神经网络模块 (network) 帮助文档\n");
        luaL_addstring(&b, "========================================\n\n");
        
        luaL_addstring(&b, "一、模块概述\n");
        luaL_addstring(&b, "----------------------------------------\n");
        luaL_addstring(&b, "network模块是一个用于创建和训练神经网络的多层感知器(MLP)模块。\n");
        luaL_addstring(&b, "支持前向传播、反向传播和多种激活函数，可用于学习任何函数映射。\n\n");
        
        luaL_addstring(&b, "二、核心概念\n");
        luaL_addstring(&b, "----------------------------------------\n");
        luaL_addstring(&b, "1. 架构(architecture): 网络各层神经元数量，如 {4, 8, 8, 1}\n");
        luaL_addstring(&b, "2. 学习率(learning_rate): 参数更新步长，通常 0.001~0.1\n");
        luaL_addstring(&b, "3. 轮数(epochs): 完整遍历数据集的次数\n");
        luaL_addstring(&b, "4. 激活函数: sigmoid, relu, tanh, linear, softmax\n\n");
        
        luaL_addstring(&b, "三、函数分类\n");
        luaL_addstring(&b, "----------------------------------------\n");
        luaL_addstring(&b, "1. 创建函数: new\n");
        luaL_addstring(&b, "2. 前向传播: forward, predict\n");
        luaL_addstring(&b, "3. 训练函数: train, trainStep\n");
        luaL_addstring(&b, "4. 属性设置: setLearningRate\n");
        luaL_addstring(&b, "5. 信息查询: info, weights, getWeights\n");
        luaL_addstring(&b, "6. 数据持久化: save, load, setWeights\n");
        luaL_addstring(&b, "7. 工具函数: clone, help\n\n");
        
        luaL_addstring(&b, "四、详细函数说明\n");
        luaL_addstring(&b, "----------------------------------------\n\n");
        
        luaL_addstring(&b, "[new(architecture, input_size, init)]\n");
        luaL_addstring(&b, "  功能: 创建神经网络\n");
        luaL_addstring(&b, "  参数:\n");
        luaL_addstring(&b, "    - architecture: 各层神经元数量的表\n");
        luaL_addstring(&b, "    - input_size: 输入特征数量\n");
        luaL_addstring(&b, "    - init: 权重初始化方法 ('he', 'xavier', 'random')\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    local net = network.new({4, 8, 8, 1}, 2)\n\n");
        
        luaL_addstring(&b, "[network:setLearningRate(rate)]\n");
        luaL_addstring(&b, "  功能: 设置学习率\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    net:setLearningRate(0.01)\n\n");
        
        luaL_addstring(&b, "[network:forward(input_table)]\n");
        luaL_addstring(&b, "  功能: 前向传播计算输出\n");
        luaL_addstring(&b, "  返回: 输出表\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    local output = net:forward({1.0, 2.0})\n\n");
        
        luaL_addstring(&b, "[network:predict(input_table)]\n");
        luaL_addstring(&b, "  功能: 预测输出（与forward相同）\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    local result = net:predict({1.0, 2.0})\n\n");
        
        luaL_addstring(&b, "[network:train(dataset, epochs, batch_size)]\n");
        luaL_addstring(&b, "  功能: 训练网络\n");
        luaL_addstring(&b, "  参数:\n");
        luaL_addstring(&b, "    - dataset: 训练数据表\n");
        luaL_addstring(&b, "    - epochs: 训练轮数（默认100）\n");
        luaL_addstring(&b, "    - batch_size: 批量大小（默认1）\n");
        luaL_addstring(&b, "  返回: 平均损失\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    local loss = net:train(data, 1000)\n\n");
        
        luaL_addstring(&b, "[network:trainStep(input, target)]\n");
        luaL_addstring(&b, "  功能: 单步训练\n");
        luaL_addstring(&b, "  返回: 当前损失\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    local loss = net:trainStep({1, 1}, {2})\n\n");
        
        luaL_addstring(&b, "[network:info()]\n");
        luaL_addstring(&b, "  功能: 获取网络信息\n");
        luaL_addstring(&b, "  返回: 信息表\n");
        luaL_addstring(&b, "  示例:\n");
        luaL_addstring(&b, "    print(net:info().is_trained)\n\n");
        
        luaL_addstring(&b, "[network:clone()]\n");
        luaL_addstring(&b, "  功能: 复制网络\n");
        luaL_addstring(&b, "  返回: 新网络\n\n");
        
        luaL_addstring(&b, "[network:weights(layer_index)]\n");
        luaL_addstring(&b, "  功能: 获取指定层权重\n");
        luaL_addstring(&b,  "  示例:\n");
        luaL_addstring(&b, "    local w = net:weights(2)\n\n");
        
        luaL_addstring(&b, "========================================\n");
        luaL_addstring(&b, "    输入 network.help('函数名') 查看详情\n");
        luaL_addstring(&b, "========================================\n");
    } else {
        luaL_addstring(&b, "\n[");
        luaL_addstring(&b, func_name);
        luaL_addstring(&b, "]\n");
        luaL_addstring(&b, "----------------------------------------\n");
        
        if (strcmp(func_name, "new") == 0) {
            luaL_addstring(&b, "函数签名: network.new(architecture, input_size[, init])\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  创建一个新的神经网络。\n\n");
            luaL_addstring(&b, "参数说明:\n");
            luaL_addstring(&b, "  - architecture: 包含各层神经元数量的表\n");
            luaL_addstring(&b, "    例如 {4, 8, 8, 1} 表示2个隐藏层(各8个神经元)和1个输出层\n");
            luaL_addstring(&b, "  - input_size: 输入特征的数量\n");
            luaL_addstring(&b, "  - init: 可选，权重初始化方法 ('he'默认, 'xavier', 'random')\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  新的网络对象\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  -- 创建用于1+1问题的网络（2输入, 1输出）\n");
            luaL_addstring(&b, "  local net = network.new({8, 8, 1}, 2)\n\n");
        } else if (strcmp(func_name, "forward") == 0) {
            luaL_addstring(&b, "函数签名: network:forward(input_table)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  对输入进行前向传播，计算网络输出。\n\n");
            luaL_addstring(&b, "参数说明:\n");
            luaL_addstring(&b, "  - input_table: 输入特征表，数量必须等于input_size\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  输出表，数量等于output_size\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local output = net:forward({1.0, 2.0})\n");
            luaL_addstring(&b, "  print(output[1])\n\n");
        } else if (strcmp(func_name, "predict") == 0) {
            luaL_addstring(&b, "函数签名: network:predict(input_table)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  使用训练好的网络进行预测。\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local result = net:predict({1.0, 2.0})\n\n");
        } else if (strcmp(func_name, "train") == 0) {
            luaL_addstring(&b, "函数签名: network:train(dataset, epochs[, batch_size])\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  使用数据集训练网络。\n\n");
            luaL_addstring(&b, "参数说明:\n");
            luaL_addstring(&b, "  - dataset: 训练数据，每个元素是 {input={...}, target={...}} 格式\n");
            luaL_addstring(&b, "  - epochs: 训练轮数\n");
            luaL_addstring(&b, "  - batch_size: 可选，小批量大小\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  平均损失值\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local data = {\n");
            luaL_addstring(&b, "      {input={0, 0}, target={0}},\n");
            luaL_addstring(&b, "      {input={0, 1}, target={1}},\n");
            luaL_addstring(&b, "      {input={1, 0}, target={1}},\n");
            luaL_addstring(&b, "      {input={1, 1}, target={2}}\n");
            luaL_addstring(&b, "  }\n");
            luaL_addstring(&b, "  local loss = net:train(data, 10000)\n\n");
        } else if (strcmp(func_name, "trainStep") == 0) {
            luaL_addstring(&b, "函数签名: network:trainStep(input_table, target_table)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  对单个样本进行一步训练。\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  当前损失值\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  net:setLearningRate(0.1)\n");
            luaL_addstring(&b, "  for i = 1, 10000 do\n");
            luaL_addstring(&b, "      local loss = net:trainStep({1, 1}, {2})\n");
            luaL_addstring(&b, "  end\n\n");
        } else if (strcmp(func_name, "info") == 0) {
            luaL_addstring(&b, "函数签名: network:info()\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  获取网络的详细信息。\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  包含以下字段的表:\n");
            luaL_addstring(&b, "    - input_size: 输入大小\n");
            luaL_addstring(&b, "    - output_size: 输出大小\n");
            luaL_addstring(&b, "    - layer_count: 层数\n");
            luaL_addstring(&b, "    - learning_rate: 学习率\n");
            luaL_addstring(&b, "    - is_trained: 是否已训练\n");
            luaL_addstring(&b, "    - epoch: 已训练轮数\n");
            luaL_addstring(&b, "    - architecture: 各层大小\n\n");
        } else if (strcmp(func_name, "clone") == 0) {
            luaL_addstring(&b, "函数签名: network:clone()\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  创建网络的深拷贝，包括所有权重。\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local net2 = net:clone()\n\n");
        } else if (strcmp(func_name, "weights") == 0) {
            luaL_addstring(&b, "函数签名: network:weights(layer_index)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  获取指定层的权重矩阵。\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local w = net:weights(2)  -- 获取第2层权重\n\n");
        } else if (strcmp(func_name, "save") == 0) {
            luaL_addstring(&b, "函数签名: network:save(filename)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  将训练好的网络保存到二进制文件。\n\n");
            luaL_addstring(&b, "参数:\n");
            luaL_addstring(&b, "  - filename: 文件路径\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  布尔值，true表示成功\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  net:train(data, 10000)\n");
            luaL_addstring(&b, "  net:save('/sdcard/add_network.bin')\n\n");
        } else if (strcmp(func_name, "load") == 0) {
            luaL_addstring(&b, "函数签名: network.load(filename)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  从二进制文件加载训练好的网络。\n\n");
            luaL_addstring(&b, "参数:\n");
            luaL_addstring(&b, "  - filename: 文件路径\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  网络对象\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local net = network.load('/sdcard/add_network.bin')\n");
            luaL_addstring(&b, "  local result = net:predict({1, 1})\n\n");
        } else if (strcmp(func_name, "getWeights") == 0) {
            luaL_addstring(&b, "函数签名: network:getWeights()\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  获取所有层的权重和偏置为Lua表。\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  表数组，每项包含:\n");
            luaL_addstring(&b, "    - input_size: 输入大小\n");
            luaL_addstring(&b, "    - output_size: 输出大小\n");
            luaL_addstring(&b, "    - activation: 激活函数名\n");
            luaL_addstring(&b, "    - weights: 权重矩阵\n");
            luaL_addstring(&b, "    - biases: 偏置向量\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local weights = net:getWeights()\n");
            luaL_addstring(&b, "  print(weights[1].weights[1][1])  -- 第1层第1行第1列权重\n\n");
        } else if (strcmp(func_name, "setWeights") == 0) {
            luaL_addstring(&b, "函数签名: network:setWeights(weights_table)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  从Lua表设置网络权重。\n\n");
            luaL_addstring(&b, "参数:\n");
            luaL_addstring(&b, "  - weights_table: 权重表（与getWeights返回格式相同）\n\n");
            luaL_addstring(&b, "返回值:\n");
            luaL_addstring(&b, "  布尔值，true表示成功\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  local weights = net:getWeights()\n");
            luaL_addstring(&b, "  -- 保存或传输weights...\n");
            luaL_addstring(&b, "  net:setWeights(weights)  -- 恢复权重\n\n");
        } else if (strcmp(func_name, "setLearningRate") == 0) {
            luaL_addstring(&b, "函数签名: network:setLearningRate(rate)\n\n");
            luaL_addstring(&b, "功能描述:\n");
            luaL_addstring(&b, "  设置网络的学习率。\n\n");
            luaL_addstring(&b, "使用示例:\n");
            luaL_addstring(&b, "  net:setLearningRate(0.01)\n\n");
        } else {
            luaL_addstring(&b, "\n[错误] 未找到函数 '");
            luaL_addstring(&b, func_name);
            luaL_addstring(&b, "'\n\n");
            luaL_addstring(&b, "可用函数: new, forward, predict, train, trainStep\n");
            luaL_addstring(&b, "          info, clone, weights, save, load\n");
            luaL_addstring(&b, "          getWeights, setWeights, setLearningRate, help\n\n");
        }
    }
    
    luaL_pushresult(&b);
    return 1;
}

/**
 * @brief 五、1+1问题完整示例
 */
static int l_network_example_add(lua_State* L) {
    luaL_Buffer b;
    luaL_buffinit(L, &b);
    
    luaL_addstring(&b, "\n========================================\n");
    luaL_addstring(&b, "    1+1问题完整训练示例\n");
    luaL_addstring(&b, "========================================\n\n");
    
    luaL_addstring(&b, "-- 1. 创建网络（2输入 -> 8 -> 8 -> 1输出）\n");
    luaL_addstring(&b, "local net = network.new({8, 8, 1}, 2)\n\n");
    
    luaL_addstring(&b, "-- 2. 设置学习率\n");
    luaL_addstring(&b, "net:setLearningRate(0.1)\n\n");
    
    luaL_addstring(&b, "-- 3. 准备训练数据\n");
    luaL_addstring(&b, "local data = {\n");
    luaL_addstring(&b, "    {input = {0, 0}, target = {0}},\n");
    luaL_addstring(&b, "    {input = {0, 1}, target = {1}},\n");
    luaL_addstring(&b, "    {input = {1, 0}, target = {1}},\n");
    luaL_addstring(&b, "    {input = {1, 1}, target = {2}}\n");
    luaL_addstring(&b, "}\n\n");
    
    luaL_addstring(&b, "-- 4. 训练网络\n");
    luaL_addstring(&b, "print('开始训练...')\n");
    luaL_addstring(&b, "local loss = net:train(data, 10000, 1)\n");
    luaL_addstring(&b, "print('最终损失: ' .. loss)\n\n");
    
    luaL_addstring(&b, "-- 5. 测试网络\n");
    luaL_addstring(&b, "print('测试结果:')\n");
    luaL_addstring(&b, "for _, d in ipairs(data) do\n");
    luaL_addstring(&b, "    local result = net:predict(d.input)\n");
    luaL_addstring(&b, "    print(d.input[1] .. ' + ' .. d.input[2] .. ' = ' .. result[1])\n");
    luaL_addstring(&b, "end\n\n");
    
    luaL_addstring(&b, "========================================\n");
    
    luaL_pushresult(&b);
    return 1;
}

/**
 * @brief 设置模块信息
 */
static void set_info(lua_State* L) {
    lua_pushliteral(L, "_COPYRIGHT");
    lua_pushliteral(L, "Copyright (C) 2026 DifierLine");
    lua_settable(L, -3);
    
    lua_pushliteral(L, "_DESCRIPTION");
    lua_pushliteral(L, "Neural Network module for Lua - 多层感知器实现");
    lua_settable(L, -3);
    
    lua_pushliteral(L, "_VERSION");
    lua_pushliteral(L, NETWORK_VERSION);
    lua_settable(L, -3);
}

/**
 * @brief 模块注册表
 */
static const struct luaL_Reg networklib[] = {
    {"new", l_network_new},
    {"help", l_network_help},
    {"example", l_network_example_add},
    {"load", l_network_load},
    {NULL, NULL}
};

/**
 * @brief 网络方法注册表
 */
static const struct luaL_Reg network_methods[] = {
    {"setLearningRate", l_network_set_learning_rate},
    {"getLearningRate", l_network_get_learning_rate},
    {"forward", l_network_forward},
    {"predict", l_network_predict},
    {"train", l_network_train},
    {"trainStep", l_network_train_step},
    {"clone", l_network_clone},
    {"info", l_network_info},
    {"weights", l_network_weights},
    {"save", l_network_save},
    {"getWeights", l_network_get_weights},
    {"setWeights", l_network_set_weights},
    {"saveWeights", l_network_save_weights},
    {NULL, NULL}
};

/**
 * @brief 网络元方法表
 */
static const struct luaL_Reg network_meta[] = {
    {"__gc", l_network_gc},
    {"__tostring", l_network_tostring},
    {NULL, NULL}
};

/**
 * @brief 模块初始化函数
 */
int luaopen_network(lua_State* L) {
    luaL_newmetatable(L, NETWORK_METATABLE);
    luaL_setfuncs(L, network_meta, 0);
    lua_pushvalue(L, -1);
    lua_setfield(L, -2, "__index");
    luaL_setfuncs(L, network_methods, 0);
    
    luaL_newlib(L, networklib);
    set_info(L);
    
    return 1;
}
