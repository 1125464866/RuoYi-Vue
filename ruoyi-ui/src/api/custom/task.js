import request from '@/utils/request'

// 查询【请填写功能名称】列表
export function listTask(query) {
  return request({
    url: '/psd/task/list',
    method: 'get',
    params: query
  })
}

// 查询【请填写功能名称】详细
export function getTask(id) {
  return request({
    url: '/psd/task/' + id,
    method: 'get'
  })
}

export function getTaskByUuid(uuid) {
  return request({
    url: '/psd/task/byUuid/' + uuid,
    method: 'get'
  })
}

// 新增【请填写功能名称】
export function addTask(data) {
  return request({
    url: '/psd/task',
    method: 'post',
    data: data,
    timeout: 999999
  })
}

// 修改【请填写功能名称】
export function updateTask(data) {
  return request({
    url: '/psd/task',
    method: 'put',
    data: data,
    timeout: 999999
  })
}

// 删除【请填写功能名称】
export function delTask(id) {
  return request({
    url: '/psd/task/' + id,
    method: 'delete'
  })
}

export function getCoze(data) {
  return request({
    url: '/psd/task/getCoze',
    method: 'post',
    data: data,
    timeout: 999999
  })
}

export function checkTask(data) {
  return request({
    url: '/psd/task/checkTask',
    method: 'post',
    data: data,
    timeout: 999999
  })
}


export function pushOfficialAccount(data) {
  return request({
    url: '/psd/task/pushOfficialAccount',
    method: 'post',
    data: data,
  })
}
