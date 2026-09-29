const _Vue = Vue

return function render(_ctx, _cache) {
  with (_ctx) {
    const { createCommentVNode: _createCommentVNode, createElementVNode: _createElementVNode, openBlock: _openBlock, createElementBlock: _createElementBlock, renderList: _renderList, Fragment: _Fragment, toDisplayString: _toDisplayString, withModifiers: _withModifiers, normalizeClass: _normalizeClass, createTextVNode: _createTextVNode, vModelSelect: _vModelSelect, withDirectives: _withDirectives, vModelDynamic: _vModelDynamic, vModelText: _vModelText, vShow: _vShow, normalizeStyle: _normalizeStyle, Transition: _Transition, withCtx: _withCtx, createVNode: _createVNode } = _Vue

    return (_openBlock(), _createElementBlock("div", {
      class: "config-panel",
      style: _normalizeStyle({ width: panelWidth + 'px' })
    }, [
      _createCommentVNode(" 拖拽调宽 "),
      _createElementVNode("div", {
        class: "config-panel-resizer",
        onMousedown: startResize,
        onTouchstartPassive: startResize
      }, [
        _createElementVNode("div", { class: "resizer-line" })
      ], 40 /* PROPS, NEED_HYDRATION */, ["onMousedown", "onTouchstartPassive"]),
      _createCommentVNode(" 顶部标题 "),
      _createElementVNode("div", { class: "config-header" }, [
        _createElementVNode("div", { class: "config-header-left" }, [
          (_openBlock(), _createElementBlock("svg", {
            class: "config-header-icon",
            width: "16",
            height: "16",
            viewBox: "0 0 24 24",
            fill: "none",
            stroke: "currentColor",
            "stroke-width": "2"
          }, [
            _createElementVNode("path", { d: "M12 2L2 7l10 5 10-5-10-5z" }),
            _createElementVNode("path", { d: "M2 17l10 5 10-5" }),
            _createElementVNode("path", { d: "M2 12l10 5 10-5" })
          ])),
          _createElementVNode("div", { class: "config-header-text" }, [
            _createElementVNode("div", {
              class: "pp-strip",
              ref: "ppStripRef"
            }, [
              (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(visibleProducts, (p) => {
                return (_openBlock(), _createElementBlock("button", {
                  key: p.id,
                  class: _normalizeClass(["pp-chip pp-chip-slot", { active: p.id === activeProductId }]),
                  onClick: _withModifiers($event => (onSelectProduct(p)), ["stop"]),
                  title: p.name
                }, [
                  _createElementVNode("span", { class: "pp-chip-name" }, _toDisplayString(p.name), 1 /* TEXT */),
                  (p.quantity !== undefined && p.quantity !== null && p.quantity !== '')
                    ? (_openBlock(), _createElementBlock("span", {
                        key: 0,
                        class: "pp-chip-qty"
                      }, "x" + _toDisplayString(p.quantity), 1 /* TEXT */))
                    : _createCommentVNode("v-if", true)
                ], 10 /* CLASS, PROPS */, ["onClick", "title"]))
              }), 128 /* KEYED_FRAGMENT */))
            ], 512 /* NEED_PATCH */)
          ])
        ]),
        _createElementVNode("div", { class: "config-header-actions" }, [
          _createElementVNode("button", {
            class: _normalizeClass(["pp-list-btn", { active: productMenuOpen }]),
            onClick: _withModifiers(toggleProductMenu, ["stop"]),
            title: "查看商品列表"
          }, [
            (_openBlock(), _createElementBlock("svg", {
              width: "15",
              height: "15",
              viewBox: "0 0 24 24",
              fill: "none",
              stroke: "currentColor",
              "stroke-width": "2"
            }, [
              _createElementVNode("line", {
                x1: "8",
                y1: "6",
                x2: "21",
                y2: "6"
              }),
              _createElementVNode("line", {
                x1: "8",
                y1: "12",
                x2: "21",
                y2: "12"
              }),
              _createElementVNode("line", {
                x1: "8",
                y1: "18",
                x2: "21",
                y2: "18"
              }),
              _createElementVNode("line", {
                x1: "3",
                y1: "6",
                x2: "3.01",
                y2: "6"
              }),
              _createElementVNode("line", {
                x1: "3",
                y1: "12",
                x2: "3.01",
                y2: "12"
              }),
              _createElementVNode("line", {
                x1: "3",
                y1: "18",
                x2: "3.01",
                y2: "18"
              })
            ])),
            _createElementVNode("span", { class: "pp-chip-name" }, "查看商品列表"),
            _createElementVNode("span", { class: "pp-count" }, _toDisplayString(products.length), 1 /* TEXT */)
          ], 10 /* CLASS, PROPS */, ["onClick"]),
          productMenuOpen
            ? (_openBlock(), _createElementBlock("div", {
                key: 0,
                class: "product-picker product-picker-menu"
              }, [
                _createElementVNode("div", { class: "pp-menu-head" }, [
                  _createTextVNode("全部商品"),
                  _createElementVNode("span", { class: "pp-menu-count" }, "元'" + _toDisplayString(products.length) + " 户", 1 /* TEXT */)
                ]),
                _createElementVNode("div", { class: "pp-menu-body" }, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(products, (p) => {
                    return (_openBlock(), _createElementBlock("div", {
                      key: p.id,
                      class: _normalizeClass(["pp-menu-item", { active: p.id === activeProductId }]),
                      onClick: _withModifiers($event => (onSelectProduct(p)), ["stop"]),
                      title: p.name
                    }, [
                      _createElementVNode("div", { class: "pp-menu-item-top" }, [
                        _createElementVNode("span", { class: "pp-menu-name" }, _toDisplayString(p.name), 1 /* TEXT */),
                        _createElementVNode("span", { class: "pp-menu-status" }, _toDisplayString(p.status), 1 /* TEXT */)
                      ]),
                      _createElementVNode("div", { class: "pp-menu-item-sub" }, [
                        _createElementVNode("span", { class: "pp-menu-code" }, _toDisplayString(p.code), 1 /* TEXT */),
                        (p.fee)
                          ? (_openBlock(), _createElementBlock("span", {
                              key: 0,
                              class: "pp-menu-fee"
                            }, _toDisplayString(p.fee) + "元/月", 1 /* TEXT */))
                          : _createCommentVNode("v-if", true),
                        (p.id === activeProductId)
                          ? (_openBlock(), _createElementBlock("svg", {
                              key: 1,
                              class: "pp-menu-check",
                              width: "14",
                              height: "14",
                              viewBox: "0 0 24 24",
                              fill: "none",
                              stroke: "currentColor",
                              "stroke-width": "3"
                            }, [
                              _createElementVNode("polyline", { points: "20 6 9 17 4 12" })
                            ]))
                          : _createCommentVNode("v-if", true)
                      ])
                    ], 10 /* CLASS, PROPS */, ["onClick", "title"]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ]))
            : _createCommentVNode("v-if", true),
          (isArchive && !copyMode)
            ? (_openBlock(), _createElementBlock("button", {
                key: 1,
                class: "config-copy-btn",
                onClick: onCopyFromArchive,
                title: "基于该存量商品复制新套餐"
              }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "14",
                  height: "14",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("rect", {
                    x: "9",
                    y: "9",
                    width: "13",
                    height: "13",
                    rx: "2"
                  }),
                  _createElementVNode("path", { d: "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" })
                ])),
                _createElementVNode("span", null, "复制")
              ], 8 /* PROPS */, ["onClick"]))
            : _createCommentVNode("v-if", true)
        ])
      ]),
      _createCommentVNode(" Tab 导航 "),
      _createElementVNode("div", { class: "config-tabs" }, [
        (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(tabs, (tab) => {
          return (_openBlock(), _createElementBlock("button", {
            key: tab.key,
            class: _normalizeClass(["config-tab", { active: activeTab === tab.key }]),
            onClick: $event => (activeTab = tab.key)
          }, [
            (tab.key === 'base')
              ? (_openBlock(), _createElementBlock("svg", {
                  key: 0,
                  width: "14",
                  height: "14",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" }),
                  _createElementVNode("polyline", { points: "14 2 14 8 20 8" })
                ]))
              : (tab.key === 'resource')
                ? (_openBlock(), _createElementBlock("svg", {
                    key: 1,
                    width: "14",
                    height: "14",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    "stroke-width": "2"
                  }, [
                    _createElementVNode("path", { d: "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" }),
                    _createElementVNode("polyline", { points: "3.27 6.96 12 12.01 20.73 6.96" }),
                    _createElementVNode("line", {
                      x1: "12",
                      y1: "22.08",
                      x2: "12",
                      y2: "12"
                    })
                  ]))
                : (tab.key === 'marketing')
                  ? (_openBlock(), _createElementBlock("svg", {
                      key: 2,
                      width: "14",
                      height: "14",
                      viewBox: "0 0 24 24",
                      fill: "none",
                      stroke: "currentColor",
                      "stroke-width": "2"
                    }, [
                      _createElementVNode("line", {
                        x1: "12",
                        y1: "1",
                        x2: "12",
                        y2: "23"
                      }),
                      _createElementVNode("path", { d: "M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" })
                    ]))
                  : (tab.key === 'sales')
                    ? (_openBlock(), _createElementBlock("svg", {
                        key: 3,
                        width: "14",
                        height: "14",
                        viewBox: "0 0 24 24",
                        fill: "none",
                        stroke: "currentColor",
                        "stroke-width": "2"
                      }, [
                        _createElementVNode("path", { d: "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" }),
                        _createElementVNode("rect", {
                          x: "8",
                          y: "2",
                          width: "8",
                          height: "4",
                          rx: "1"
                        }),
                        _createElementVNode("path", { d: "M9 14l2 2 4-4" })
                      ]))
                    : (_openBlock(), _createElementBlock("svg", {
                        key: 4,
                        width: "14",
                        height: "14",
                        viewBox: "0 0 24 24",
                        fill: "none",
                        stroke: "currentColor",
                        "stroke-width": "2"
                      }, [
                        _createElementVNode("path", { d: "M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" }),
                        _createElementVNode("path", { d: "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" })
                      ])),
            _createTextVNode(" " + _toDisplayString(tab.label), 1 /* TEXT */)
          ], 10 /* CLASS, PROPS */, ["onClick"]))
        }), 128 /* KEYED_FRAGMENT */))
      ]),
      _createCommentVNode(" Tab 内容￀?"),
      _createElementVNode("div", { class: "config-body" }, [
        _createCommentVNode(" 基础信息 Tab "),
        _createCommentVNode(" 配置信息 Tab（合并：基础信息/月租/语音/流量/短信/前项限制/后项限制，'"),
        _withDirectives(_createElementVNode("div", { class: "tab-content" }, [
          _createElementVNode("div", { class: "form-section" }, [
            _createElementVNode("div", { class: "section-header open" }, [
              _createElementVNode("span", { class: "section-title" }, "基础信息")
            ]),
            _createElementVNode("div", { class: "section-content base-info-grid" }, [
              (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(baseFields, (field) => {
                return (_openBlock(), _createElementBlock("div", {
                  class: "form-row base-field-row",
                  key: field.key
                }, [
                  _createElementVNode("label", { class: "form-label" }, [
                    (field.required)
                      ? (_openBlock(), _createElementBlock("span", {
                          key: 0,
                          class: "required"
                        }, "*"))
                      : _createCommentVNode("v-if", true),
                    _createElementVNode("span", { class: "label-text" }, _toDisplayString(field.label), 1 /* TEXT */),
                    (fieldTag(field) === 'ai')
                      ? (_openBlock(), _createElementBlock("span", {
                          key: 1,
                          class: "field-tag tag-ai",
                          "data-reason": aiFilled[field.key] || field.tagReason
                        }, "AI推荐", 8 /* PROPS */, ["data-reason"]))
                      : (fieldTag(field) === 'reuse')
                        ? (_openBlock(), _createElementBlock("span", {
                            key: 2,
                            class: "field-tag tag-reuse",
                            "data-reason": field.tagReason
                          }, "复用", 8 /* PROPS */, ["data-reason"]))
                        : (fieldTag(field) === 'parsed')
                          ? (_openBlock(), _createElementBlock("span", {
                              key: 3,
                              class: "field-tag tag-parsed",
                              "data-reason": parsedFilled[field.key] || '根据用户需求解析得出'
                            }, "已解析", 8 /* PROPS */, ["data-reason"]))
                          : _createCommentVNode("v-if", true)
                  ]),
                  _createElementVNode("div", { class: "form-control" }, [
                    (field.options)
                      ? _withDirectives((_openBlock(), _createElementBlock("select", {
                          key: 0,
                          "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                          class: _normalizeClass(["form-input form-select", { modified: modifiedFields[field.key] }]),
                          disabled: submitted,
                          onChange: $event => (markModified(field.key))
                        }, [
                          (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.options, (opt) => {
                            return (_openBlock(), _createElementBlock("option", {
                              key: opt.value,
                              value: opt.value
                            }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                          }), 128 /* KEYED_FRAGMENT */))
                        ], 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onChange"])), [
                          [_vModelSelect, formData[field.key]]
                        ])
                      : _withDirectives((_openBlock(), _createElementBlock("input", {
                          key: 1,
                          type: field.type || 'text',
                          "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                          class: _normalizeClass(["form-input", { modified: modifiedFields[field.key] }]),
                          disabled: submitted,
                          onInput: $event => (markModified(field.key))
                        }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"])), [
                          [_vModelDynamic, formData[field.key]]
                        ])
                  ])
                ]))
              }), 128 /* KEYED_FRAGMENT */))
            ])
          ]),
          (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(marketingSections, (section) => {
            return (_openBlock(), _createElementBlock("div", {
              key: section.key,
              class: "form-section"
            }, [
              _createElementVNode("div", {
                class: _normalizeClass(["section-header", { open: expandedSections[section.key] }]),
                onClick: $event => (toggleSection(section.key))
              }, [
                _createElementVNode("span", { class: "section-title" }, _toDisplayString(section.title), 1 /* TEXT */),
                (_openBlock(), _createElementBlock("svg", {
                  class: "section-chevron",
                  width: "14",
                  height: "14",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("polyline", { points: "6 9 12 15 18 9" })
                ]))
              ], 10 /* CLASS, PROPS */, ["onClick"]),
              _withDirectives(_createElementVNode("div", { class: "section-content" }, [
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(section.fields, (field) => {
                  return (_openBlock(), _createElementBlock("div", {
                    class: "form-row",
                    key: field.key
                  }, [
                    _createElementVNode("label", { class: "form-label" }, [
                      (field.required)
                        ? (_openBlock(), _createElementBlock("span", {
                            key: 0,
                            class: "required"
                          }, "*"))
                        : _createCommentVNode("v-if", true),
                      _createElementVNode("span", { class: "label-text" }, _toDisplayString(field.label), 1 /* TEXT */),
                      (fieldTag(field) === 'ai')
                        ? (_openBlock(), _createElementBlock("span", {
                            key: 1,
                            class: "field-tag tag-ai",
                            "data-reason": aiFilled[field.key] || field.tagReason
                          }, "AI推荐", 8 /* PROPS */, ["data-reason"]))
                        : (fieldTag(field) === 'reuse')
                          ? (_openBlock(), _createElementBlock("span", {
                              key: 2,
                              class: "field-tag tag-reuse",
                              "data-reason": field.tagReason
                            }, "复用", 8 /* PROPS */, ["data-reason"]))
                          : (fieldTag(field) === 'parsed')
                            ? (_openBlock(), _createElementBlock("span", {
                                key: 3,
                                class: "field-tag tag-parsed",
                                "data-reason": parsedFilled[field.key] || '根据用户需求解析得出'
                              }, "已解析", 8 /* PROPS */, ["data-reason"]))
                            : _createCommentVNode("v-if", true)
                    ]),
                    _createElementVNode("div", { class: "form-control" }, [
                      (field.unit)
                        ? (_openBlock(), _createElementBlock("div", {
                            key: 0,
                            class: "input-with-unit"
                          }, [
                            _withDirectives(_createElementVNode("input", {
                              type: field.type || 'text',
                              "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                              class: _normalizeClass(["form-input", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                              disabled: submitted,
                              onInput: $event => (markModified(field.key))
                            }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"]), [
                              [_vModelDynamic, formData[field.key]]
                            ]),
                            _createElementVNode("span", { class: "input-unit" }, _toDisplayString(field.unit), 1 /* TEXT */)
                          ]))
                        : (field.type === 'select')
                          ? _withDirectives((_openBlock(), _createElementBlock("select", {
                              key: 1,
                              "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                              class: _normalizeClass(["form-input form-select", { modified: modifiedFields[field.key] }]),
                              disabled: submitted,
                              onChange: $event => (markModified(field.key))
                            }, [
                              (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.options, (opt) => {
                                return (_openBlock(), _createElementBlock("option", {
                                  key: opt.value,
                                  value: opt.value
                                }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                              }), 128 /* KEYED_FRAGMENT */))
                            ], 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onChange"])), [
                              [_vModelSelect, formData[field.key]]
                            ])
                          : (field.type === 'textarea')
                            ? _withDirectives((_openBlock(), _createElementBlock("textarea", {
                                key: 2,
                                "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                class: _normalizeClass(["form-input form-textarea", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                disabled: submitted,
                                rows: "3",
                                onInput: $event => (markModified(field.key))
                              }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onInput"])), [
                                [_vModelText, formData[field.key]]
                              ])
                            : _withDirectives((_openBlock(), _createElementBlock("input", {
                                key: 3,
                                type: field.type || 'text',
                                "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                class: _normalizeClass(["form-input", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                disabled: submitted,
                                onInput: $event => (markModified(field.key))
                              }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"])), [
                                [_vModelDynamic, formData[field.key]]
                              ])
                    ])
                  ]))
                }), 128 /* KEYED_FRAGMENT */))
              ], 512 /* NEED_PATCH */), [
                [_vShow, expandedSections[section.key]]
              ])
            ]))
          }), 128 /* KEYED_FRAGMENT */)),
          (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(resourceSections, (section) => {
            return (_openBlock(), _createElementBlock("div", {
              key: section.key,
              class: "form-section"
            }, [
              _createElementVNode("div", {
                class: _normalizeClass(["section-header", { open: expandedSections[section.key] }]),
                onClick: $event => (toggleSection(section.key))
              }, [
                _createElementVNode("span", { class: "section-title" }, _toDisplayString(section.title), 1 /* TEXT */),
                (_openBlock(), _createElementBlock("svg", {
                  class: "section-chevron",
                  width: "14",
                  height: "14",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("polyline", { points: "6 9 12 15 18 9" })
                ]))
              ], 10 /* CLASS, PROPS */, ["onClick"]),
              _withDirectives(_createElementVNode("div", { class: "section-content" }, [
                _createCommentVNode(" table 类型：表标'+ 添加按钮 "),
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(section.fields, (field) => {
                  return (_openBlock(), _createElementBlock(_Fragment, { key: field.key }, [
                    (field.type === 'table')
                      ? (_openBlock(), _createElementBlock("div", {
                          key: 0,
                          class: "table-field"
                        }, [
                          _createElementVNode("div", { class: "table-field-header" }, [
                            _createElementVNode("label", { class: "form-label table-field-label" }, [
                              _createElementVNode("span", { class: "label-text" }, _toDisplayString(field.label), 1 /* TEXT */),
                              (fieldTag(field) === 'ai')
                                ? (_openBlock(), _createElementBlock("span", {
                                    key: 0,
                                    class: "field-tag tag-ai",
                                    "data-reason": aiFilled[field.key] || field.tagReason
                                  }, "AI推荐", 8 /* PROPS */, ["data-reason"]))
                                : (fieldTag(field) === 'reuse')
                                  ? (_openBlock(), _createElementBlock("span", {
                                      key: 1,
                                      class: "field-tag tag-reuse",
                                      "data-reason": field.tagReason
                                    }, "复用", 8 /* PROPS */, ["data-reason"]))
                                  : (fieldTag(field) === 'parsed')
                                    ? (_openBlock(), _createElementBlock("span", {
                                        key: 2,
                                        class: "field-tag tag-parsed",
                                        "data-reason": parsedFilled[field.key] || '根据用户需求解析得出'
                                      }, "已解析", 8 /* PROPS */, ["data-reason"]))
                                    : _createCommentVNode("v-if", true)
                            ]),
                            _createElementVNode("button", {
                              class: "table-add-btn",
                              disabled: submitted,
                              onClick: $event => (addTableRow(field.key, field.columns))
                            }, [
                              (_openBlock(), _createElementBlock("svg", {
                                width: "12",
                                height: "12",
                                viewBox: "0 0 24 24",
                                fill: "none",
                                stroke: "currentColor",
                                "stroke-width": "2.5"
                              }, [
                                _createElementVNode("line", {
                                  x1: "12",
                                  y1: "5",
                                  x2: "12",
                                  y2: "19"
                                }),
                                _createElementVNode("line", {
                                  x1: "5",
                                  y1: "12",
                                  x2: "19",
                                  y2: "12"
                                })
                              ])),
                              _createTextVNode(" 添加 ")
                            ], 8 /* PROPS */, ["disabled", "onClick"])
                          ]),
                          _createElementVNode("div", { class: "limit-table-wrap" }, [
                            _createElementVNode("table", { class: "limit-table" }, [
                              _createElementVNode("thead", null, [
                                _createElementVNode("tr", null, [
                                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.columns, (col) => {
                                    return (_openBlock(), _createElementBlock("th", { key: col.key }, _toDisplayString(col.label), 1 /* TEXT */))
                                  }), 128 /* KEYED_FRAGMENT */)),
                                  _createElementVNode("th", { class: "col-action" }, "操作")
                                ])
                              ]),
                              _createElementVNode("tbody", null, [
                                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(tableRows(field.key), (row, rowIdx) => {
                                  return (_openBlock(), _createElementBlock("tr", { key: rowIdx }, [
                                    (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.columns, (col) => {
                                      return (_openBlock(), _createElementBlock("td", { key: col.key }, [
                                        (col.type === 'auto')
                                          ? (_openBlock(), _createElementBlock("div", {
                                              key: 0,
                                              class: "table-cell-auto"
                                            }, _toDisplayString(generatePreLimitTip(field.key, rowIdx)), 1 /* TEXT */))
                                          : (col.type === 'select' && col.dynamicOptions)
                                            ? (_openBlock(), _createElementBlock("select", {
                                                key: 1,
                                                value: row[col.key],
                                                class: "table-cell-input table-cell-select",
                                                disabled: submitted,
                                                onChange: $event => (onDynamicOptionChange(field.key, rowIdx, col.key, $event))
                                              }, [
                                                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(getDynamicOptions(col, row), (opt) => {
                                                  return (_openBlock(), _createElementBlock("option", {
                                                    key: opt.value,
                                                    value: opt.value
                                                  }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                                                }), 128 /* KEYED_FRAGMENT */))
                                              ], 40 /* PROPS, NEED_HYDRATION */, ["value", "disabled", "onChange"]))
                                            : (col.type === 'select')
                                              ? (_openBlock(), _createElementBlock("select", {
                                                  key: 2,
                                                  value: row[col.key],
                                                  class: "table-cell-input table-cell-select",
                                                  disabled: submitted,
                                                  onChange: $event => (onSelectCellChange(field.key, rowIdx, col.key, $event))
                                                }, [
                                                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(col.options, (opt) => {
                                                    return (_openBlock(), _createElementBlock("option", {
                                                      key: opt.value,
                                                      value: opt.value
                                                    }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                                                  }), 128 /* KEYED_FRAGMENT */))
                                                ], 40 /* PROPS, NEED_HYDRATION */, ["value", "disabled", "onChange"]))
                                              : (_openBlock(), _createElementBlock("input", {
                                                  key: 3,
                                                  type: "text",
                                                  value: row[col.key],
                                                  class: "table-cell-input",
                                                  placeholder: col.placeholder || '',
                                                  disabled: submitted,
                                                  onInput: $event => (onInputCellChange(field.key, rowIdx, col.key, $event))
                                                }, null, 40 /* PROPS, NEED_HYDRATION */, ["value", "placeholder", "disabled", "onInput"]))
                                      ]))
                                    }), 128 /* KEYED_FRAGMENT */)),
                                    _createElementVNode("td", { class: "col-action" }, [
                                      _createElementVNode("button", {
                                        class: "table-del-btn",
                                        disabled: submitted,
                                        onClick: $event => (deleteTableRow(field.key, rowIdx)),
                                        title: "删除"
                                      }, [
                                        (_openBlock(), _createElementBlock("svg", {
                                          width: "14",
                                          height: "14",
                                          viewBox: "0 0 24 24",
                                          fill: "none",
                                          stroke: "currentColor",
                                          "stroke-width": "2"
                                        }, [
                                          _createElementVNode("polyline", { points: "3 6 5 6 21 6" }),
                                          _createElementVNode("path", { d: "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" })
                                        ]))
                                      ], 8 /* PROPS */, ["disabled", "onClick"])
                                    ])
                                  ]))
                                }), 128 /* KEYED_FRAGMENT */)),
                                (tableRows(field.key).length === 0)
                                  ? (_openBlock(), _createElementBlock("tr", { key: 0 }, [
                                      _createElementVNode("td", {
                                        colspan: field.columns.length + 1,
                                        class: "table-empty"
                                      }, "暂无数据，点击\"添加\"按钮新增", 8 /* PROPS */, ["colspan"])
                                    ]))
                                  : _createCommentVNode("v-if", true)
                              ])
                            ])
                          ])
                        ]))
                      : (_openBlock(), _createElementBlock(_Fragment, { key: 1 }, [
                          _createCommentVNode(" 常规字段 "),
                          _createElementVNode("div", { class: "form-row" }, [
                            _createElementVNode("label", { class: "form-label" }, [
                              (field.required)
                                ? (_openBlock(), _createElementBlock("span", {
                                    key: 0,
                                    class: "required"
                                  }, "*"))
                                : _createCommentVNode("v-if", true),
                              _createElementVNode("span", { class: "label-text" }, _toDisplayString(field.label), 1 /* TEXT */),
                              (fieldTag(field) === 'ai')
                                ? (_openBlock(), _createElementBlock("span", {
                                    key: 1,
                                    class: "field-tag tag-ai",
                                    "data-reason": aiFilled[field.key] || field.tagReason
                                  }, "AI推荐", 8 /* PROPS */, ["data-reason"]))
                                : (fieldTag(field) === 'reuse')
                                  ? (_openBlock(), _createElementBlock("span", {
                                      key: 2,
                                      class: "field-tag tag-reuse",
                                      "data-reason": field.tagReason
                                    }, "复用", 8 /* PROPS */, ["data-reason"]))
                                  : (fieldTag(field) === 'parsed')
                                    ? (_openBlock(), _createElementBlock("span", {
                                        key: 3,
                                        class: "field-tag tag-parsed",
                                        "data-reason": parsedFilled[field.key] || '根据用户需求解析得出'
                                      }, "已解析", 8 /* PROPS */, ["data-reason"]))
                                    : _createCommentVNode("v-if", true)
                            ]),
                            _createElementVNode("div", { class: "form-control" }, [
                              (field.unit)
                                ? (_openBlock(), _createElementBlock("div", {
                                    key: 0,
                                    class: "input-with-unit"
                                  }, [
                                    _withDirectives(_createElementVNode("input", {
                                      type: field.type || 'text',
                                      "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                      class: _normalizeClass(["form-input", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                      disabled: submitted,
                                      onInput: $event => (markModified(field.key))
                                    }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"]), [
                                      [_vModelDynamic, formData[field.key]]
                                    ]),
                                    _createElementVNode("span", { class: "input-unit" }, _toDisplayString(field.unit), 1 /* TEXT */)
                                  ]))
                                : (field.type === 'select')
                                  ? _withDirectives((_openBlock(), _createElementBlock("select", {
                                      key: 1,
                                      "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                      class: _normalizeClass(["form-input form-select", { modified: modifiedFields[field.key] }]),
                                      disabled: submitted,
                                      onChange: $event => (markModified(field.key))
                                    }, [
                                      (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.options, (opt) => {
                                        return (_openBlock(), _createElementBlock("option", {
                                          key: opt.value,
                                          value: opt.value
                                        }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                                      }), 128 /* KEYED_FRAGMENT */))
                                    ], 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onChange"])), [
                                      [_vModelSelect, formData[field.key]]
                                    ])
                                  : (field.type === 'textarea')
                                    ? _withDirectives((_openBlock(), _createElementBlock("textarea", {
                                        key: 2,
                                        "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                        class: _normalizeClass(["form-input form-textarea", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                        disabled: submitted,
                                        rows: "3",
                                        onInput: $event => (markModified(field.key))
                                      }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onInput"])), [
                                        [_vModelText, formData[field.key]]
                                      ])
                                    : _withDirectives((_openBlock(), _createElementBlock("input", {
                                        key: 3,
                                        type: field.type || 'text',
                                        "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                        class: _normalizeClass(["form-input", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                        disabled: submitted,
                                        onInput: $event => (markModified(field.key))
                                      }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"])), [
                                        [_vModelDynamic, formData[field.key]]
                                      ])
                            ])
                          ])
                        ], 2112 /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */))
                  ], 64 /* STABLE_FRAGMENT */))
                }), 128 /* KEYED_FRAGMENT */))
              ], 512 /* NEED_PATCH */), [
                [_vShow, expandedSections[section.key]]
              ])
            ]))
          }), 128 /* KEYED_FRAGMENT */)),
          (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(salesSections, (section) => {
            return (_openBlock(), _createElementBlock("div", {
              key: section.key,
              class: "form-section"
            }, [
              _createElementVNode("div", {
                class: _normalizeClass(["section-header", { open: expandedSections[section.key] }]),
                onClick: $event => (toggleSection(section.key))
              }, [
                _createElementVNode("span", { class: "section-title" }, _toDisplayString(section.title), 1 /* TEXT */),
                (_openBlock(), _createElementBlock("svg", {
                  class: "section-chevron",
                  width: "14",
                  height: "14",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("polyline", { points: "6 9 12 15 18 9" })
                ]))
              ], 10 /* CLASS, PROPS */, ["onClick"]),
              _withDirectives(_createElementVNode("div", { class: "section-content" }, [
                _createCommentVNode(" 遍历 section 内字 "),
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(section.fields, (field) => {
                  return (_openBlock(), _createElementBlock(_Fragment, { key: field.key }, [
                    _createCommentVNode(" table 类型：表￀'+ 添加按钮 "),
                    (field.type === 'table')
                      ? (_openBlock(), _createElementBlock("div", {
                          key: 0,
                          class: "table-field"
                        }, [
                          _createElementVNode("div", { class: "table-field-header" }, [
                            _createElementVNode("label", { class: "form-label table-field-label" }, [
                              _createElementVNode("span", { class: "label-text" }, _toDisplayString(field.label), 1 /* TEXT */),
                              (fieldTag(field) === 'ai')
                                ? (_openBlock(), _createElementBlock("span", {
                                    key: 0,
                                    class: "field-tag tag-ai",
                                    "data-reason": aiFilled[field.key] || field.tagReason
                                  }, "AI推荐", 8 /* PROPS */, ["data-reason"]))
                                : (fieldTag(field) === 'reuse')
                                  ? (_openBlock(), _createElementBlock("span", {
                                      key: 1,
                                      class: "field-tag tag-reuse",
                                      "data-reason": field.tagReason
                                    }, "复用", 8 /* PROPS */, ["data-reason"]))
                                  : (fieldTag(field) === 'parsed')
                                    ? (_openBlock(), _createElementBlock("span", {
                                        key: 2,
                                        class: "field-tag tag-parsed",
                                        "data-reason": parsedFilled[field.key] || '根据用户需求解析得出'
                                      }, "已解析", 8 /* PROPS */, ["data-reason"]))
                                    : _createCommentVNode("v-if", true)
                            ]),
                            _createElementVNode("button", {
                              class: "table-add-btn",
                              disabled: submitted,
                              onClick: $event => (addTableRow(field.key, field.columns))
                            }, [
                              (_openBlock(), _createElementBlock("svg", {
                                width: "12",
                                height: "12",
                                viewBox: "0 0 24 24",
                                fill: "none",
                                stroke: "currentColor",
                                "stroke-width": "2.5"
                              }, [
                                _createElementVNode("line", {
                                  x1: "12",
                                  y1: "5",
                                  x2: "12",
                                  y2: "19"
                                }),
                                _createElementVNode("line", {
                                  x1: "5",
                                  y1: "12",
                                  x2: "19",
                                  y2: "12"
                                })
                              ])),
                              _createTextVNode(" 添加 ")
                            ], 8 /* PROPS */, ["disabled", "onClick"])
                          ]),
                          _createElementVNode("div", { class: "limit-table-wrap" }, [
                            _createElementVNode("table", { class: "limit-table" }, [
                              _createElementVNode("thead", null, [
                                _createElementVNode("tr", null, [
                                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.columns, (col) => {
                                    return (_openBlock(), _createElementBlock("th", { key: col.key }, _toDisplayString(col.label), 1 /* TEXT */))
                                  }), 128 /* KEYED_FRAGMENT */)),
                                  _createElementVNode("th", { class: "col-action" }, "操作")
                                ])
                              ]),
                              _createElementVNode("tbody", null, [
                                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(tableRows(field.key), (row, rowIdx) => {
                                  return (_openBlock(), _createElementBlock("tr", { key: rowIdx }, [
                                    (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.columns, (col) => {
                                      return (_openBlock(), _createElementBlock("td", { key: col.key }, [
                                        _createCommentVNode(" auto 类型：只读，根据同行其他单元格自动生成提示语 "),
                                        (col.type === 'auto')
                                          ? (_openBlock(), _createElementBlock("div", {
                                              key: 0,
                                              class: "table-cell-auto"
                                            }, _toDisplayString(generatePreLimitTip(field.key, rowIdx)), 1 /* TEXT */))
                                          : (col.type === 'select' && col.dynamicOptions)
                                            ? (_openBlock(), _createElementBlock(_Fragment, { key: 1 }, [
                                                _createCommentVNode(" dynamicOptions 类型：选项依赖同行另一个字段的 "),
                                                _createElementVNode("select", {
                                                  value: row[col.key],
                                                  class: "table-cell-input table-cell-select",
                                                  disabled: submitted,
                                                  onChange: $event => (onDynamicOptionChange(field.key, rowIdx, col.key, $event))
                                                }, [
                                                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(getDynamicOptions(col, row), (opt) => {
                                                    return (_openBlock(), _createElementBlock("option", {
                                                      key: opt.value,
                                                      value: opt.value
                                                    }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                                                  }), 128 /* KEYED_FRAGMENT */))
                                                ], 40 /* PROPS, NEED_HYDRATION */, ["value", "disabled", "onChange"])
                                              ], 2112 /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */))
                                            : (col.type === 'select')
                                              ? (_openBlock(), _createElementBlock(_Fragment, { key: 2 }, [
                                                  _createCommentVNode(" 普通select "),
                                                  _createElementVNode("select", {
                                                    value: row[col.key],
                                                    class: "table-cell-input table-cell-select",
                                                    disabled: submitted,
                                                    onChange: $event => (onSelectCellChange(field.key, rowIdx, col.key, $event))
                                                  }, [
                                                    (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(col.options, (opt) => {
                                                      return (_openBlock(), _createElementBlock("option", {
                                                        key: opt.value,
                                                        value: opt.value
                                                      }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                                                    }), 128 /* KEYED_FRAGMENT */))
                                                  ], 40 /* PROPS, NEED_HYDRATION */, ["value", "disabled", "onChange"])
                                                ], 2112 /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */))
                                              : (_openBlock(), _createElementBlock(_Fragment, { key: 3 }, [
                                                  _createCommentVNode(" 普通input "),
                                                  _createElementVNode("input", {
                                                    type: "text",
                                                    value: row[col.key],
                                                    class: "table-cell-input",
                                                    placeholder: col.placeholder || '',
                                                    disabled: submitted,
                                                    onInput: $event => (onInputCellChange(field.key, rowIdx, col.key, $event))
                                                  }, null, 40 /* PROPS, NEED_HYDRATION */, ["value", "placeholder", "disabled", "onInput"])
                                                ], 2112 /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */))
                                      ]))
                                    }), 128 /* KEYED_FRAGMENT */)),
                                    _createElementVNode("td", { class: "col-action" }, [
                                      _createElementVNode("button", {
                                        class: "table-del-btn",
                                        disabled: submitted,
                                        onClick: $event => (deleteTableRow(field.key, rowIdx)),
                                        title: "删除"
                                      }, [
                                        (_openBlock(), _createElementBlock("svg", {
                                          width: "14",
                                          height: "14",
                                          viewBox: "0 0 24 24",
                                          fill: "none",
                                          stroke: "currentColor",
                                          "stroke-width": "2"
                                        }, [
                                          _createElementVNode("polyline", { points: "3 6 5 6 21 6" }),
                                          _createElementVNode("path", { d: "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" })
                                        ]))
                                      ], 8 /* PROPS */, ["disabled", "onClick"])
                                    ])
                                  ]))
                                }), 128 /* KEYED_FRAGMENT */)),
                                (tableRows(field.key).length === 0)
                                  ? (_openBlock(), _createElementBlock("tr", { key: 0 }, [
                                      _createElementVNode("td", {
                                        colspan: field.columns.length + 1,
                                        class: "table-empty"
                                      }, "暂无数据，点击\"添加\"按钮新增", 8 /* PROPS */, ["colspan"])
                                    ]))
                                  : _createCommentVNode("v-if", true)
                              ])
                            ])
                          ])
                        ]))
                      : (_openBlock(), _createElementBlock(_Fragment, { key: 1 }, [
                          _createCommentVNode(" 常规字段：checkbox-group / select / input "),
                          _createElementVNode("div", { class: "form-row" }, [
                            _createElementVNode("label", { class: "form-label" }, [
                              (field.required)
                                ? (_openBlock(), _createElementBlock("span", {
                                    key: 0,
                                    class: "required"
                                  }, "*"))
                                : _createCommentVNode("v-if", true),
                              _createElementVNode("span", { class: "label-text" }, _toDisplayString(field.label), 1 /* TEXT */),
                              (fieldTag(field) === 'ai')
                                ? (_openBlock(), _createElementBlock("span", {
                                    key: 1,
                                    class: "field-tag tag-ai",
                                    "data-reason": aiFilled[field.key] || field.tagReason
                                  }, "AI推荐", 8 /* PROPS */, ["data-reason"]))
                                : (fieldTag(field) === 'reuse')
                                  ? (_openBlock(), _createElementBlock("span", {
                                      key: 2,
                                      class: "field-tag tag-reuse",
                                      "data-reason": field.tagReason
                                    }, "复用", 8 /* PROPS */, ["data-reason"]))
                                  : (fieldTag(field) === 'parsed')
                                    ? (_openBlock(), _createElementBlock("span", {
                                        key: 3,
                                        class: "field-tag tag-parsed",
                                        "data-reason": parsedFilled[field.key] || '根据用户需求解析得出'
                                      }, "已解析", 8 /* PROPS */, ["data-reason"]))
                                    : _createCommentVNode("v-if", true)
                            ]),
                            _createElementVNode("div", { class: "form-control" }, [
                              _createCommentVNode(" checkbox-group: 多选框 "),
                              (field.type === 'checkbox-group')
                                ? (_openBlock(), _createElementBlock("div", {
                                    key: 0,
                                    class: "checkbox-group"
                                  }, [
                                    (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.options, (opt) => {
                                      return (_openBlock(), _createElementBlock("label", {
                                        key: opt.value,
                                        class: _normalizeClass(["checkbox-item", { checked: checkboxValue(field.key, opt.value) }])
                                      }, [
                                        _createElementVNode("input", {
                                          type: "checkbox",
                                          value: opt.value,
                                          checked: checkboxValue(field.key, opt.value),
                                          disabled: submitted,
                                          onChange: $event => (toggleCheckbox(field.key, opt.value))
                                        }, null, 40 /* PROPS, NEED_HYDRATION */, ["value", "checked", "disabled", "onChange"]),
                                        _createElementVNode("span", null, _toDisplayString(opt.label), 1 /* TEXT */)
                                      ], 2 /* CLASS */))
                                    }), 128 /* KEYED_FRAGMENT */))
                                  ]))
                                : (field.unit)
                                  ? (_openBlock(), _createElementBlock("div", {
                                      key: 1,
                                      class: "input-with-unit"
                                    }, [
                                      _withDirectives(_createElementVNode("input", {
                                        type: field.type || 'text',
                                        "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                        class: _normalizeClass(["form-input", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                        disabled: submitted,
                                        onInput: $event => (markModified(field.key))
                                      }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"]), [
                                        [_vModelDynamic, formData[field.key]]
                                      ]),
                                      _createElementVNode("span", { class: "input-unit" }, _toDisplayString(field.unit), 1 /* TEXT */)
                                    ]))
                                  : (field.type === 'select')
                                    ? _withDirectives((_openBlock(), _createElementBlock("select", {
                                        key: 2,
                                        "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                        class: _normalizeClass(["form-input form-select", { modified: modifiedFields[field.key] }]),
                                        disabled: submitted,
                                        onChange: $event => (markModified(field.key))
                                      }, [
                                        (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(field.options, (opt) => {
                                          return (_openBlock(), _createElementBlock("option", {
                                            key: opt.value,
                                            value: opt.value
                                          }, _toDisplayString(opt.label), 9 /* TEXT, PROPS */, ["value"]))
                                        }), 128 /* KEYED_FRAGMENT */))
                                      ], 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onChange"])), [
                                        [_vModelSelect, formData[field.key]]
                                      ])
                                    : (field.type === 'textarea')
                                      ? _withDirectives((_openBlock(), _createElementBlock("textarea", {
                                          key: 3,
                                          "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                          class: _normalizeClass(["form-input form-textarea", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                          disabled: submitted,
                                          rows: "3",
                                          onInput: $event => (markModified(field.key))
                                        }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["onUpdate:modelValue", "disabled", "onInput"])), [
                                          [_vModelText, formData[field.key]]
                                        ])
                                      : _withDirectives((_openBlock(), _createElementBlock("input", {
                                          key: 4,
                                          type: field.type || 'text',
                                          "onUpdate:modelValue": $event => ((formData[field.key]) = $event),
                                          class: _normalizeClass(["form-input", { modified: modifiedFields[field.key], highlight: field.highlight }]),
                                          disabled: submitted,
                                          onInput: $event => (markModified(field.key))
                                        }, null, 42 /* CLASS, PROPS, NEED_HYDRATION */, ["type", "onUpdate:modelValue", "disabled", "onInput"])), [
                                          [_vModelDynamic, formData[field.key]]
                                        ])
                            ])
                          ])
                        ], 2112 /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */))
                  ], 64 /* STABLE_FRAGMENT */))
                }), 128 /* KEYED_FRAGMENT */))
              ], 512 /* NEED_PATCH */), [
                [_vShow, expandedSections[section.key]]
              ])
            ]))
          }), 128 /* KEYED_FRAGMENT */))
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'config']
        ]),
        _createCommentVNode(" 需求提报单 Tab "),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { class: "rv-card" }, [
            _createElementVNode("div", { class: "rv-head" }, [
              _createElementVNode("div", { class: "rv-title" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "16",
                  height: "16",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" }),
                  _createElementVNode("polyline", { points: "14 2 14 8 20 8" })
                ])),
                _createElementVNode("span", null, "需求提报单")
              ]),
              _createElementVNode("span", { class: "rv-badge badge-blue" }, _toDisplayString(demand.name), 1 /* TEXT */)
            ]),
            _createElementVNode("div", { class: "rv-grid grid-2" }, [
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "提报编号"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(demand.no), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "提报于"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(demand.applicant), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "提报部门"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(demand.department), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "提报日期"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(demand.date), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "目标客户美"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(demand.target), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "期望生效日期"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(demand.effective), 1 /* TEXT */)
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "业务需求概过"),
              _createElementVNode("div", { class: "rv-p" }, _toDisplayString(demand.require), 1 /* TEXT */)
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "销售渠道"),
              _createElementVNode("div", { class: "rv-tags" }, [
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(demand.channels, (c) => {
                  return (_openBlock(), _createElementBlock("span", {
                    key: c,
                    class: "rv-tag"
                  }, _toDisplayString(c), 1 /* TEXT */))
                }), 128 /* KEYED_FRAGMENT */))
              ])
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'demand']
        ]),
        _createCommentVNode(" 加载方案 Tab "),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { class: "rv-card" }, [
            _createElementVNode("div", { class: "rv-head" }, [
              _createElementVNode("div", { class: "rv-title" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "16",
                  height: "16",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" }),
                  _createElementVNode("polyline", { points: "14 2 14 8 20 8" })
                ])),
                _createElementVNode("span", null, "加载方案")
              ]),
              _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(scheme.status), 1 /* TEXT */)
            ]),
            _createElementVNode("div", { class: "rv-grid grid-2" }, [
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "方案编号"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(scheme.no), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "关联需求单"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(scheme.requireNo), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "方案名称"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(scheme.name), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "方案版本 / 编制"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(scheme.version) + " · " + _toDisplayString(scheme.author), 1 /* TEXT */)
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "方案要点"),
              _createElementVNode("div", { class: "rv-summary" }, [
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(scheme.summary, (row, i) => {
                  return (_openBlock(), _createElementBlock("div", {
                    key: i,
                    class: "rv-sum-row"
                  }, [
                    _createElementVNode("span", { class: "rv-sum-k" }, _toDisplayString(row[0]), 1 /* TEXT */),
                    _createElementVNode("span", { class: "rv-sum-v" }, _toDisplayString(row[1]), 1 /* TEXT */)
                  ]))
                }), 128 /* KEYED_FRAGMENT */))
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "加载配置模块"),
              _createElementVNode("div", { class: "rv-tags" }, [
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(scheme.items, (it) => {
                  return (_openBlock(), _createElementBlock("span", {
                    key: it,
                    class: "rv-tag"
                  }, _toDisplayString(it), 1 /* TEXT */))
                }), 128 /* KEYED_FRAGMENT */))
              ])
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'scheme']
        ]),
        _createCommentVNode(" 稽核报告 Tab "),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { class: "rv-card" }, [
            _createElementVNode("div", { class: "rv-head" }, [
              _createElementVNode("div", { class: "rv-title" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "16",
                  height: "16",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M9 12l2 2 4-4" }),
                  _createElementVNode("circle", {
                    cx: "12",
                    cy: "12",
                    r: "10"
                  })
                ])),
                _createElementVNode("span", null, "配置规格稽核报告")
              ]),
              _createElementVNode("span", { class: "rv-badge badge-green" }, "稽核通过")
            ]),
            _createElementVNode("div", { class: "rv-strip" }, [
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "稽核对象"),
                _createElementVNode("span", { class: "rv-v mono" }, _toDisplayString(audit.object), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "稽核时间"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(audit.time), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "稽核结果"),
                _createElementVNode("span", {
                  class: "rv-v",
                  style: {"color":"#16a34a","font-weight":"600"}
                }, _toDisplayString(audit.status), 1 /* TEXT */)
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "稽核检查项"),
              _createElementVNode("table", { class: "rv-table" }, [
                _createElementVNode("thead", null, [
                  _createElementVNode("tr", null, [
                    _createElementVNode("th", null, "检查项"),
                    _createElementVNode("th", { style: {"width":"120px"} }, "结果")
                  ])
                ]),
                _createElementVNode("tbody", null, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(audit.checks, (c, i) => {
                    return (_openBlock(), _createElementBlock("tr", { key: i }, [
                      _createElementVNode("td", null, _toDisplayString(c[0]), 1 /* TEXT */),
                      _createElementVNode("td", null, [
                        _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(c[1]), 1 /* TEXT */)
                      ])
                    ]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ])
            ]),
            _createElementVNode("div", { class: "rv-concl concl-pass" }, [
              _createElementVNode("span", { class: "rv-cc-icon" }, "，"),
              _createElementVNode("span", null, _toDisplayString(audit.conclusion), 1 /* TEXT */)
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'audit']
        ]),
        _createCommentVNode(" 校准报告 Tab "),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { class: "rv-card" }, [
            _createElementVNode("div", { class: "rv-head" }, [
              _createElementVNode("div", { class: "rv-title" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "16",
                  height: "16",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("circle", {
                    cx: "12",
                    cy: "12",
                    r: "10"
                  }),
                  _createElementVNode("path", { d: "M12 6v6l4 2" })
                ])),
                _createElementVNode("span", null, "资费校准报告")
              ]),
              _createElementVNode("span", { class: "rv-badge badge-green" }, "校准通过")
            ]),
            _createElementVNode("div", { class: "rv-strip" }, [
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "校准对象"),
                _createElementVNode("span", { class: "rv-v mono" }, _toDisplayString(calibrate.object), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "校准时间"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(calibrate.time), 1 /* TEXT */)
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "比对明细（套餐需求'vs 计费配置，"),
              _createElementVNode("table", { class: "rv-table" }, [
                _createElementVNode("thead", null, [
                  _createElementVNode("tr", null, [
                    _createElementVNode("th", null, "项目"),
                    _createElementVNode("th", null, "套餐描述（需求）"),
                    _createElementVNode("th", null, "计费配置描述（系统）"),
                    _createElementVNode("th", { style: {"width":"100px"} }, "比对结果")
                  ])
                ]),
                _createElementVNode("tbody", null, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(calibrate.rows, (r, i) => {
                    return (_openBlock(), _createElementBlock("tr", { key: i }, [
                      _createElementVNode("td", null, _toDisplayString(r[0]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(r[1]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(r[2]), 1 /* TEXT */),
                      _createElementVNode("td", null, [
                        _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(r[3]), 1 /* TEXT */)
                      ])
                    ]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ])
            ]),
            _createElementVNode("div", { class: "rv-concl concl-pass" }, [
              _createElementVNode("span", { class: "rv-cc-icon" }, "，"),
              _createElementVNode("span", null, _toDisplayString(calibrate.conclusion), 1 /* TEXT */)
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'calibrate']
        ]),
        _createCommentVNode(" 测试报告 Tab "),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { class: "rv-card" }, [
            _createElementVNode("div", { class: "rv-head" }, [
              _createElementVNode("div", { class: "rv-title" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "16",
                  height: "16",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("polygon", { points: "12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26" })
                ])),
                _createElementVNode("span", null, "销售品自动化测试报告")
              ]),
              _createElementVNode("span", { class: "rv-badge badge-blue" }, "建议上线")
            ]),
            _createElementVNode("div", { class: "rv-metrics" }, [
              _createElementVNode("div", { class: "rv-metric" }, [
                _createElementVNode("span", { class: "rv-m-num" }, _toDisplayString(testRep.total), 1 /* TEXT */),
                _createElementVNode("span", { class: "rv-m-tt" }, "总用例数")
              ]),
              _createElementVNode("div", { class: "rv-metric" }, [
                _createElementVNode("span", {
                  class: "rv-m-num",
                  style: {"color":"#16a34a"}
                }, _toDisplayString(testRep.pass), 1 /* TEXT */),
                _createElementVNode("span", { class: "rv-m-tt" }, "通过用例")
              ]),
              _createElementVNode("div", { class: "rv-metric" }, [
                _createElementVNode("span", {
                  class: "rv-m-num",
                  style: {"color":"#16a34a"}
                }, _toDisplayString(testRep.rate), 1 /* TEXT */),
                _createElementVNode("span", { class: "rv-m-tt" }, "通过率")
              ]),
              _createElementVNode("div", { class: "rv-metric" }, [
                _createElementVNode("span", {
                  class: "rv-m-num",
                  style: {"color":"#2563eb"}
                }, _toDisplayString(testRep.conclusion), 1 /* TEXT */),
                _createElementVNode("span", { class: "rv-m-tt" }, "整体结论")
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "三大验证维度"),
              _createElementVNode("table", { class: "rv-table" }, [
                _createElementVNode("thead", null, [
                  _createElementVNode("tr", null, [
                    _createElementVNode("th", null, "验证维度"),
                    _createElementVNode("th", null, "用例数"),
                    _createElementVNode("th", null, "通过"),
                    _createElementVNode("th", { style: {"width":"100px"} }, "结果")
                  ])
                ]),
                _createElementVNode("tbody", null, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(testRep.dims, (d, i) => {
                    return (_openBlock(), _createElementBlock("tr", { key: i }, [
                      _createElementVNode("td", null, _toDisplayString(d[0]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(d[1]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(d[2]), 1 /* TEXT */),
                      _createElementVNode("td", null, [
                        _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(d[3]), 1 /* TEXT */)
                      ])
                    ]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "重点用例（节选）"),
              _createElementVNode("table", { class: "rv-table" }, [
                _createElementVNode("thead", null, [
                  _createElementVNode("tr", null, [
                    _createElementVNode("th", null, "用例ID"),
                    _createElementVNode("th", null, "用例名称"),
                    _createElementVNode("th", { style: {"width":"80px"} }, "等级"),
                    _createElementVNode("th", { style: {"width":"100px"} }, "结果")
                  ])
                ]),
                _createElementVNode("tbody", null, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(testRep.cases, (c, i) => {
                    return (_openBlock(), _createElementBlock("tr", { key: i }, [
                      _createElementVNode("td", null, _toDisplayString(c[0]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(c[1]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(c[2]), 1 /* TEXT */),
                      _createElementVNode("td", null, [
                        _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(c[3]), 1 /* TEXT */)
                      ])
                    ]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "受理验证报告（并入自动化测试，"),
              _createElementVNode("div", {
                class: "rv-strip",
                style: {"margin-bottom":"10px"}
              }, [
                _createElementVNode("div", { class: "rv-cell" }, [
                  _createElementVNode("span", { class: "rv-k" }, "受理凭证 orderId"),
                  _createElementVNode("span", { class: "rv-v mono" }, _toDisplayString(verify.orderId), 1 /* TEXT */)
                ]),
                _createElementVNode("div", { class: "rv-cell" }, [
                  _createElementVNode("span", { class: "rv-k" }, "offerInstId"),
                  _createElementVNode("span", { class: "rv-v mono" }, _toDisplayString(verify.offerInstId), 1 /* TEXT */)
                ]),
                _createElementVNode("div", { class: "rv-cell" }, [
                  _createElementVNode("span", { class: "rv-k" }, "验证时间"),
                  _createElementVNode("span", { class: "rv-v" }, _toDisplayString(verify.time), 1 /* TEXT */)
                ])
              ]),
              _createElementVNode("table", { class: "rv-table" }, [
                _createElementVNode("thead", null, [
                  _createElementVNode("tr", null, [
                    _createElementVNode("th", null, "受理场景"),
                    _createElementVNode("th", { style: {"width":"120px"} }, "结果")
                  ])
                ]),
                _createElementVNode("tbody", null, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(verify.scenes, (s, i) => {
                    return (_openBlock(), _createElementBlock("tr", { key: i }, [
                      _createElementVNode("td", null, _toDisplayString(s[0]), 1 /* TEXT */),
                      _createElementVNode("td", null, [
                        _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(s[1]), 1 /* TEXT */)
                      ])
                    ]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ]),
              _createElementVNode("div", {
                class: "rv-k",
                style: {"margin-top":"12px"}
              }, "关键测点"),
              _createElementVNode("div", { class: "rv-tags" }, [
                (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(verify.points, (p, i) => {
                  return (_openBlock(), _createElementBlock("span", {
                    key: i,
                    class: "rv-tag tag-ok"
                  }, "，'" + _toDisplayString(p[0]) + " · " + _toDisplayString(p[1]), 1 /* TEXT */))
                }), 128 /* KEYED_FRAGMENT */))
              ])
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'testrep']
        ]),
        _createCommentVNode(" 审批矩阵 Tab "),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { class: "rv-card" }, [
            _createElementVNode("div", { class: "rv-head" }, [
              _createElementVNode("div", { class: "rv-title" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "16",
                  height: "16",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M22 11.08V12a10 10 0 1 1-5.93-9.14" }),
                  _createElementVNode("polyline", { points: "22 4 12 14.01 9 11.01" })
                ])),
                _createElementVNode("span", null, "上线审批矩阵")
              ]),
              _createElementVNode("span", { class: "rv-badge badge-green" }, "已通过")
            ]),
            _createElementVNode("div", { class: "rv-strip" }, [
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "审批单号"),
                _createElementVNode("span", { class: "rv-v mono" }, _toDisplayString(approval.no), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "关联销售品"),
                _createElementVNode("span", { class: "rv-v" }, _toDisplayString(approval.name), 1 /* TEXT */)
              ]),
              _createElementVNode("div", { class: "rv-cell" }, [
                _createElementVNode("span", { class: "rv-k" }, "状态"),
                _createElementVNode("span", {
                  class: "rv-v",
                  style: {"color":"#16a34a","font-weight":"600"}
                }, _toDisplayString(approval.status), 1 /* TEXT */)
              ])
            ]),
            _createElementVNode("div", { class: "rv-block" }, [
              _createElementVNode("div", { class: "rv-k" }, "审批流转矩阵"),
              _createElementVNode("table", { class: "rv-table" }, [
                _createElementVNode("thead", null, [
                  _createElementVNode("tr", null, [
                    _createElementVNode("th", { style: {"width":"60px"} }, "序号"),
                    _createElementVNode("th", null, "审批节点"),
                    _createElementVNode("th", null, "审批角色"),
                    _createElementVNode("th", { style: {"width":"90px"} }, "状态"),
                    _createElementVNode("th", null, "审批意见"),
                    _createElementVNode("th", { style: {"width":"170px"} }, "更新时间")
                  ])
                ]),
                _createElementVNode("tbody", null, [
                  (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(approval.nodes, (n, i) => {
                    return (_openBlock(), _createElementBlock("tr", { key: i }, [
                      _createElementVNode("td", null, _toDisplayString(i + 1), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(n[0]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(n[1]), 1 /* TEXT */),
                      _createElementVNode("td", null, [
                        _createElementVNode("span", { class: "rv-badge badge-green" }, _toDisplayString(n[2]), 1 /* TEXT */)
                      ]),
                      _createElementVNode("td", null, _toDisplayString(n[3]), 1 /* TEXT */),
                      _createElementVNode("td", null, _toDisplayString(n[4]), 1 /* TEXT */)
                    ]))
                  }), 128 /* KEYED_FRAGMENT */))
                ])
              ])
            ]),
            _createElementVNode("div", { class: "rv-concl concl-pass" }, [
              _createElementVNode("span", { class: "rv-cc-icon" }, "，"),
              _createElementVNode("span", null, _toDisplayString(approval.note), 1 /* TEXT */)
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'approval']
        ]),
        _createCommentVNode(" 运营视图：整项'ECharts 大盘（同文档内联，CSS 作用基'#ops-view-root，按比例缩放适配，'"),
        _withDirectives(_createElementVNode("div", { class: "tab-content rv-wrap" }, [
          _createElementVNode("div", { id: "ops-view-root" }, [
            _createElementVNode("div", { class: "ops-scaler" }, [
              _createCommentVNode(" ===== 顶部导航 ===== "),
              _createElementVNode("div", { class: "topbar" }, [
                _createElementVNode("div", { class: "logo" }, [
                  _createElementVNode("div", { class: "logo-mark" }, "的"),
                  _createTextVNode(" 产品运营管理平台 ")
                ]),
                _createElementVNode("div", { class: "nav-tabs" }),
                _createElementVNode("div", { class: "topbar-right" }, [
                  _createElementVNode("span", null, "2026-09-15"),
                  _createElementVNode("div", { class: "avatar" }, "式"),
                  _createElementVNode("span", null, "管理告")
                ])
              ]),
              _createElementVNode("div", { class: "layout" }, [
                _createElementVNode("main", { class: "main" }, [
                  _createCommentVNode(" ======================================================\r\n         视图一：单品运营配置（刚上线'· 无数据）\r\n    ======================================================= "),
                  _createElementVNode("section", {
                    id: "view-single",
                    class: "view active"
                  }, [
                    _createElementVNode("div", {
                      class: "card",
                      style: {"padding":"16px 20px"}
                    }, [
                      _createElementVNode("div", { style: {"display":"flex","justify-content":"space-between","align-items":"center","flex-wrap":"wrap","gap":"12px"} }, [
                        _createElementVNode("div", null, [
                          _createElementVNode("div", { style: {"font-size":"17px","font-weight":"700","display":"flex","align-items":"center","gap":"10px"} }, [
                            _createTextVNode(" 5G-A套餐159元 "),
                            _createElementVNode("span", { style: {"display":"inline-flex","align-items":"center","gap":"5px","font-size":"11.5px","font-weight":"600","padding":"3px 11px","border-radius":"20px","background":"var(--green-bg)","color":"var(--green)"} }, [
                              _createElementVNode("span", { style: {"width":"6px","height":"6px","border-radius":"50%","background":"currentColor"} }),
                              _createTextVNode(" 已上线?· 监控初始化中 ")
                            ])
                          ]),
                          _createElementVNode("div", { style: {"font-size":"11.5px","color":"var(--text-lt)","margin-top":"4px"} }, " 产品编码，?00102308  ·  上线时间，?026-09-15 10:24  ·  归属：省公司市场部 ")
                        ]),
                        _createElementVNode("div", { style: {"display":"flex","gap":"8px"} }, [
                          _createElementVNode("button", {
                            class: "btn btn-sm btn-primary",
                            onclick: "switchView('board')"
                          }, "查看运营视图")
                        ])
                      ])
                    ]),
                    _createElementVNode("div", {
                      class: "card",
                      style: {"padding":"14px 18px","background":"#FBFCFE","border":"1px solid #D3DEEC"}
                    }, [
                      _createElementVNode("div", { style: {"display":"flex","gap":"12px","font-size":"12.5px","line-height":"1.6","color":"#00704D"} }, [
                        _createElementVNode("div", { style: {"width":"20px","height":"20px","border-radius":"5px","background":"var(--green)","color":"#fff","display":"flex","align-items":"center","justify-content":"center","font-size":"11px","font-weight":"700","flex-shrink":"0"} }, "，"),
                        _createElementVNode("div", null, [
                          _createElementVNode("b", null, "配置、稽核、校准、测试、受理验证已全部通过"),
                          _createTextVNode("，OA 上线审批已完成。 当前产品进入监控运维阶段，因刚上线暂无实际数据，以下一?"),
                          _createElementVNode("b", null, "通用经营指标口径"),
                          _createTextVNode(" 一'"),
                          _createElementVNode("b", null, "预警推送规则"),
                          _createTextVNode("，确认后系统将自 T+1 日出账起自动采集并触发监控。 ")
                        ])
                      ])
                    ]),
                    _createCommentVNode(" 指标字典与预警规则'")
                  ]),
                  _createCommentVNode(" ======================================================\r\n         视图二：驾驶舱风格调度大盘（仅通用经营指标）\r\n    ======================================================= "),
                  _createElementVNode("section", {
                    id: "view-board",
                    class: "view"
                  }, [
                    _createCommentVNode(" 顶部：面包屑 + 月份选择（参考首页工具栏，?"),
                    _createElementVNode("div", { style: {"display":"flex","align-items":"center","justify-content":"space-between","height":"44px","margin-bottom":"6px"} }, [
                      _createElementVNode("div", { style: {"font-size":"13px"} }, [
                        _createElementVNode("span", { style: {"color":"#4b5563","font-weight":"500"} }, "全省大盘")
                      ]),
                      _createElementVNode("div", { style: {"position":"relative","display":"inline-flex","align-items":"center"} }, [
                        _createElementVNode("input", {
                          type: "month",
                          id: "boardMonthPicker",
                          value: "2026-09",
                          title: "选择月份",
                          style: {"padding":"8px 36px 8px 12px","border":"1px solid #d1d5db","border-radius":"6px","font-size":"13px","color":"#374151","background":"#fff","appearance":"none","-webkit-appearance":"none","font-family":"inherit"}
                        }),
                        _createElementVNode("span", { style: {"position":"absolute","right":"10px","color":"#9ca3af","pointer-events":"none","font-size":"13px"} }, "📅")
                      ])
                    ]),
                    _createCommentVNode(" 第一排：已'= 全省系列收入，'/3），可'= 健康度分析（1/3，'"),
                    _createElementVNode("div", { class: "main-grid" }, [
                      _createCommentVNode(" 左：系列收入总卡 "),
                      _createElementVNode("div", { class: "market-revenue-card" }, [
                        _createElementVNode("div", { class: "section-title" }, "市场收入"),
                        _createElementVNode("div", { class: "market-revenue-cols" }, [
                          _createCommentVNode(" 累计收入 "),
                          _createElementVNode("div", { class: "market-revenue-col" }, [
                            _createElementVNode("div", { style: {"display":"flex","align-items":"flex-start","gap":"12px"} }, [
                              _createElementVNode("div", { class: "mr-icon mr-icon-blue" }, "🖥"),
                              _createElementVNode("div", null, [
                                _createElementVNode("div", { style: {"font-size":"13px","color":"#4b5563","font-weight":"500"} }, "累计收入"),
                                _createElementVNode("div", { class: "mr-value-line" }, [
                                  _createElementVNode("div", { class: "mr-value" }, [
                                    _createTextVNode("1023.21"),
                                    _createElementVNode("span", { class: "unit" }, "万元")
                                  ]),
                                  _createElementVNode("div", { style: {"font-size":"12.5px","display":"flex","align-items":"center","gap":"6px","white-space":"nowrap"} }, [
                                    _createElementVNode("span", { style: {"color":"#374151"} }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-down" }, [
                                      _createTextVNode("-10.1% "),
                                      _createElementVNode("i", { class: "caret" }, "▼")
                                    ])
                                  ])
                                ])
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 当月收入 "),
                          _createElementVNode("div", { class: "market-revenue-col" }, [
                            _createElementVNode("div", { style: {"display":"flex","align-items":"flex-start","gap":"12px"} }, [
                              _createElementVNode("div", { class: "mr-icon mr-icon-amber" }, "👤"),
                              _createElementVNode("div", null, [
                                _createElementVNode("div", { style: {"font-size":"13px","color":"#4b5563","font-weight":"500"} }, "当月收入"),
                                _createElementVNode("div", { class: "mr-value-line" }, [
                                  _createElementVNode("div", { class: "mr-value" }, [
                                    _createTextVNode("980.55"),
                                    _createElementVNode("span", { class: "unit" }, "万元")
                                  ]),
                                  _createElementVNode("div", { style: {"font-size":"12.5px","display":"flex","align-items":"center","gap":"6px","white-space":"nowrap"} }, [
                                    _createElementVNode("span", { style: {"color":"#374151"} }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-up" }, [
                                      _createTextVNode("3.6% "),
                                      _createElementVNode("i", { class: "caret" }, "▲")
                                    ])
                                  ])
                                ])
                              ])
                            ])
                          ])
                        ]),
                        _createCommentVNode(" 内嵌三系列收入：通信服务收入→'G-A系列 / 算力服务收入→权益随心选系列'/ 智能服务收入→融合套餐系列'"),
                        _createElementVNode("div", { class: "series-revenue-inner" }, [
                          _createElementVNode("div", { class: "series-grid" }, [
                            _createCommentVNode(" 5G-A系列收入 "),
                            _createElementVNode("div", { class: "series-col" }, [
                              _createElementVNode("div", { class: "series-head" }, [
                                _createElementVNode("div", {
                                  class: "series-head-icon",
                                  style: {"background":"#dbeafe","color":"#2563eb"}
                                }, "📶"),
                                _createElementVNode("div", { class: "series-head-name" }, "5G-A系列收入")
                              ]),
                              _createElementVNode("div", { class: "series-pair" }, [
                                _createElementVNode("div", null, [
                                  _createElementVNode("div", { class: "sp-label" }, "累计收入"),
                                  _createElementVNode("div", { class: "sp-value" }, [
                                    _createTextVNode("613.45"),
                                    _createElementVNode("span", { class: "unit" }, "户")
                                  ]),
                                  _createElementVNode("div", { class: "sp-yoy" }, [
                                    _createElementVNode("span", { class: "k" }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-up" }, [
                                      _createTextVNode("2.7% "),
                                      _createElementVNode("i", { class: "caret" }, "▲")
                                    ])
                                  ])
                                ]),
                                _createElementVNode("div", null, [
                                  _createElementVNode("div", { class: "sp-label" }, "当月收入"),
                                  _createElementVNode("div", { class: "sp-value" }, [
                                    _createTextVNode("420.18"),
                                    _createElementVNode("span", { class: "unit" }, "户")
                                  ]),
                                  _createElementVNode("div", { class: "sp-yoy" }, [
                                    _createElementVNode("span", { class: "k" }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-down" }, [
                                      _createTextVNode("-5.2% "),
                                      _createElementVNode("i", { class: "caret" }, "▼")
                                    ])
                                  ])
                                ])
                              ])
                            ]),
                            _createCommentVNode(" 权益随心选系列收入?"),
                            _createElementVNode("div", { class: "series-col" }, [
                              _createElementVNode("div", { class: "series-head" }, [
                                _createElementVNode("div", {
                                  class: "series-head-icon",
                                  style: {"background":"#d1fae5","color":"#059669"}
                                }, "🎛"),
                                _createElementVNode("div", { class: "series-head-name" }, "权益随心选系列收入")
                              ]),
                              _createElementVNode("div", { class: "series-pair" }, [
                                _createElementVNode("div", null, [
                                  _createElementVNode("div", { class: "sp-label" }, "累计收入"),
                                  _createElementVNode("div", { class: "sp-value" }, [
                                    _createTextVNode("245.67"),
                                    _createElementVNode("span", { class: "unit" }, "户")
                                  ]),
                                  _createElementVNode("div", { class: "sp-yoy" }, [
                                    _createElementVNode("span", { class: "k" }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-down" }, [
                                      _createTextVNode("-6.2% "),
                                      _createElementVNode("i", { class: "caret" }, "▼")
                                    ])
                                  ])
                                ]),
                                _createElementVNode("div", null, [
                                  _createElementVNode("div", { class: "sp-label" }, "当月收入"),
                                  _createElementVNode("div", { class: "sp-value" }, [
                                    _createTextVNode("180.34"),
                                    _createElementVNode("span", { class: "unit" }, "户")
                                  ]),
                                  _createElementVNode("div", { class: "sp-yoy" }, [
                                    _createElementVNode("span", { class: "k" }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-up" }, [
                                      _createTextVNode("1.9% "),
                                      _createElementVNode("i", { class: "caret" }, "▲")
                                    ])
                                  ])
                                ])
                              ])
                            ]),
                            _createCommentVNode(" 融合套餐系列收入 "),
                            _createElementVNode("div", { class: "series-col" }, [
                              _createElementVNode("div", { class: "series-head" }, [
                                _createElementVNode("div", {
                                  class: "series-head-icon",
                                  style: {"background":"#ffedd5","color":"#ea580c"}
                                }, "🧩"),
                                _createElementVNode("div", { class: "series-head-name" }, "融合套餐系列收入")
                              ]),
                              _createElementVNode("div", { class: "series-pair" }, [
                                _createElementVNode("div", null, [
                                  _createElementVNode("div", { class: "sp-label" }, "累计收入"),
                                  _createElementVNode("div", { class: "sp-value" }, [
                                    _createTextVNode("188.22"),
                                    _createElementVNode("span", { class: "unit" }, "户")
                                  ]),
                                  _createElementVNode("div", { class: "sp-yoy" }, [
                                    _createElementVNode("span", { class: "k" }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-down" }, [
                                      _createTextVNode("-4.8% "),
                                      _createElementVNode("i", { class: "caret" }, "▼")
                                    ])
                                  ])
                                ]),
                                _createElementVNode("div", null, [
                                  _createElementVNode("div", { class: "sp-label" }, "当月收入"),
                                  _createElementVNode("div", { class: "sp-value" }, [
                                    _createTextVNode("150.91"),
                                    _createElementVNode("span", { class: "unit" }, "户")
                                  ]),
                                  _createElementVNode("div", { class: "sp-yoy" }, [
                                    _createElementVNode("span", { class: "k" }, "月同比"),
                                    _createElementVNode("span", { class: "yoy-up" }, [
                                      _createTextVNode("2.3% "),
                                      _createElementVNode("i", { class: "caret" }, "▲")
                                    ])
                                  ])
                                ])
                              ])
                            ])
                          ])
                        ])
                      ]),
                      _createCommentVNode(" 右：健康度分析'"),
                      _createElementVNode("div", { class: "health-analysis-card" }, [
                        _createElementVNode("div", { class: "section-title" }, "健康度分析"),
                        _createElementVNode("div", { class: "health-analysis" }, [
                          _createElementVNode("div", { class: "health-count-list" }, [
                            _createElementVNode("div", { class: "health-count-row health-count-row--total" }, [
                              _createElementVNode("span", null, "套餐总数"),
                              _createElementVNode("span", { class: "value" }, "10户")
                            ]),
                            _createElementVNode("div", {
                              class: "health-count-row health-count-row--warn",
                              "data-dropdown": "dropdown-warn"
                            }, [
                              _createElementVNode("span", null, "预警套餐数"),
                              _createElementVNode("span", null, [
                                _createElementVNode("span", { class: "value" }, "4户"),
                                _createElementVNode("i", { class: "trigger" }, "▼")
                              ]),
                              _createElementVNode("div", {
                                id: "dropdown-warn",
                                class: "health-warn-menu"
                              }, [
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A套餐单品299元"),
                                  _createElementVNode("span", { class: "sc sc-yellow" }, "59")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A融合套餐199元"),
                                  _createElementVNode("span", { class: "sc sc-red" }, "52")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "权益随心通09元"),
                                  _createElementVNode("span", { class: "sc sc-red" }, "58")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "权益随心通?9元"),
                                  _createElementVNode("span", { class: "sc sc-red" }, "48")
                                ])
                              ])
                            ]),
                            _createElementVNode("div", {
                              class: "health-count-row health-count-row--warn",
                              "data-dropdown": "dropdown-excellent"
                            }, [
                              _createElementVNode("span", null, "健康套餐数"),
                              _createElementVNode("span", null, [
                                _createElementVNode("span", { class: "value" }, "3户"),
                                _createElementVNode("i", { class: "trigger" }, "▼")
                              ]),
                              _createElementVNode("div", {
                                id: "dropdown-excellent",
                                class: "health-warn-menu"
                              }, [
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A融合套餐199元"),
                                  _createElementVNode("span", { class: "sc sc-green" }, "91")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A套餐159元"),
                                  _createElementVNode("span", { class: "sc sc-green" }, "88")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A融合套餐239元"),
                                  _createElementVNode("span", { class: "sc sc-green" }, "87")
                                ])
                              ])
                            ]),
                            _createElementVNode("div", {
                              class: "health-count-row health-count-row--warn",
                              "data-dropdown": "dropdown-good"
                            }, [
                              _createElementVNode("span", null, "亚健康套餐数"),
                              _createElementVNode("span", null, [
                                _createElementVNode("span", { class: "value" }, "3户"),
                                _createElementVNode("i", { class: "trigger" }, "▼")
                              ]),
                              _createElementVNode("div", {
                                id: "dropdown-good",
                                class: "health-warn-menu"
                              }, [
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A套餐单品199元"),
                                  _createElementVNode("span", { class: "sc sc-yellow" }, "79")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A融合套餐299元"),
                                  _createElementVNode("span", { class: "sc sc-yellow" }, "74")
                                ]),
                                _createElementVNode("div", { class: "item" }, [
                                  _createElementVNode("span", null, "5G-A套餐单品239元"),
                                  _createElementVNode("span", { class: "sc sc-yellow" }, "65")
                                ])
                              ])
                            ])
                          ]),
                          _createElementVNode("div", { class: "health-pie-wrap" }, [
                            _createElementVNode("div", { class: "health-pie-figure" }, [
                              (_openBlock(), _createElementBlock("svg", {
                                id: "healthPieSvg",
                                width: "220",
                                height: "100",
                                viewBox: "0 0 220 100",
                                role: "img",
                                "aria-label": "套餐健康度分布"
                              }, [
                                _createElementVNode("ellipse", {
                                  cx: "110",
                                  cy: "82",
                                  rx: "78",
                                  ry: "6",
                                  fill: "rgba(29, 78, 216, 0.12)"
                                }),
                                _createElementVNode("ellipse", {
                                  cx: "110",
                                  cy: "50",
                                  rx: "58",
                                  ry: "16",
                                  stroke: "#2563eb",
                                  "stroke-width": "3",
                                  fill: "none",
                                  opacity: "0.18"
                                }),
                                _createElementVNode("g", {
                                  id: "healthPieBottom",
                                  transform: "translate(0,10) scale(1,0.40) translate(0,64)"
                                }),
                                _createElementVNode("g", {
                                  id: "healthPieTop",
                                  transform: "scale(1,0.40) translate(0,64)"
                                })
                              ]))
                            ]),
                            _createElementVNode("div", { class: "health-pie-legend" }, [
                              _createElementVNode("div", { class: "health-pie-legend-row" }, [
                                _createElementVNode("div", { class: "health-pie-legend-left" }, [
                                  _createElementVNode("span", {
                                    class: "health-pie-dot",
                                    style: {"background":"#0ea5ff"}
                                  }),
                                  _createElementVNode("span", { class: "health-pie-legend-label" }, "健康套餐（≥80分）")
                                ]),
                                _createElementVNode("span", {
                                  class: "health-pie-legend-value",
                                  style: {"color":"#0ea5ff"}
                                }, "30%")
                              ]),
                              _createElementVNode("div", { class: "health-pie-legend-row" }, [
                                _createElementVNode("div", { class: "health-pie-legend-left" }, [
                                  _createElementVNode("span", {
                                    class: "health-pie-dot",
                                    style: {"background":"#ff2a7a"}
                                  }),
                                  _createElementVNode("span", { class: "health-pie-legend-label" }, "亚健康套餐（60-80分）")
                                ]),
                                _createElementVNode("span", {
                                  class: "health-pie-legend-value",
                                  style: {"color":"#ff2a7a"}
                                }, "40%")
                              ]),
                              _createElementVNode("div", {
                                class: "health-pie-legend-row",
                                style: {"padding-bottom":"0"}
                              }, [
                                _createElementVNode("div", { class: "health-pie-legend-left" }, [
                                  _createElementVNode("span", {
                                    class: "health-pie-dot",
                                    style: {"background":"#ff7a00"}
                                  }),
                                  _createElementVNode("span", { class: "health-pie-legend-label" }, "预警套餐，'lt;60分）")
                                ]),
                                _createElementVNode("span", {
                                  class: "health-pie-legend-value",
                                  style: {"color":"#ff7a00"}
                                }, "30%")
                              ])
                            ])
                          ])
                        ])
                      ])
                    ]),
                    _createCommentVNode(" 第二排：单套餐收入下钻（tab 切换 + 导出 + 双图，?"),
                    _createElementVNode("div", { class: "series-revenue-box" }, [
                      _createElementVNode("div", { class: "sr-toolbar" }, [
                        _createElementVNode("div", { class: "sr-tabs" }, [
                          _createElementVNode("button", {
                            type: "button",
                            class: "sr-tab active",
                            "data-tab": "wga"
                          }, "5G-A系列收入"),
                          _createElementVNode("button", {
                            type: "button",
                            class: "sr-tab",
                            "data-tab": "quanyi"
                          }, "权益随心选系列收入"),
                          _createElementVNode("button", {
                            type: "button",
                            class: "sr-tab",
                            "data-tab": "ronghe"
                          }, "融合套餐系列收入")
                        ]),
                        _createElementVNode("button", {
                          type: "button",
                          class: "sr-export",
                          id: "srExportBtn"
                        }, "⇩导出")
                      ]),
                      _createElementVNode("div", {
                        class: "sr-content",
                        id: "srContent"
                      })
                    ]),
                    _createCommentVNode(" 第三排：套餐下钻列表 "),
                    _createElementVNode("div", {
                      class: "card",
                      id: "board-list"
                    }, [
                      _createElementVNode("div", { class: "card-head" }, [
                        _createElementVNode("div", { class: "card-title" }, "下钻至全量套餐"),
                        _createElementVNode("div", { class: "card-sub" }, "点击单个套餐进入健康度运营视图")
                      ]),
                      _createElementVNode("div", { class: "prod-grid" }, [
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A套餐159元','900102308','单品')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A套餐159元"),
                              _createElementVNode("div", { class: "prod-code" }, "900102308")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-green" }, "88")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-single" }, "单品"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "12.5 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "1.82 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--red)"} }, "+16.4%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A套餐单品199元','900113045','单品')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A套餐单品199元"),
                              _createElementVNode("div", { class: "prod-code" }, "900113045")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-green" }, "85")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-single" }, "单品"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "8.3 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "1.14 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--red)"} }, "+12.6%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A套餐单品239元','900113043','单品')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A套餐单品239元"),
                              _createElementVNode("div", { class: "prod-code" }, "900113043")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-green" }, "82")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-single" }, "单品"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "6.7 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "0.86 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--red)"} }, "+8.2%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A套餐单品299元','900102310','单品')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A套餐单品299元"),
                              _createElementVNode("div", { class: "prod-code" }, "900102310")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-yellow" }, "79")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-single" }, "单品"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "4.2 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "0.42 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--green)"} }, "-3.6%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A融合套餐199元','900113046','融合')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A融合套餐199元"),
                              _createElementVNode("div", { class: "prod-code" }, "900113046")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-green" }, "91")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-fusion" }, "融合"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "15.8 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "2.14 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--red)"} }, "+21.8%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A融合套餐199元','900102306','融合')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A融合套餐199元"),
                              _createElementVNode("div", { class: "prod-code" }, "900102306")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-yellow" }, "74")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-fusion" }, "融合"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "3.6 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "0.32 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--green)"} }, "-7.4%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A融合套餐239元','900113044','融合')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A融合套餐239元"),
                              _createElementVNode("div", { class: "prod-code" }, "900113044")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-green" }, "87")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-fusion" }, "融合"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "9.4 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "1.26 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--red)"} }, "+14.2%")
                            ])
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "prod-card",
                          onclick: "openHealth('5G-A融合套餐299元','900102307','融合')"
                        }, [
                          _createElementVNode("div", { class: "prod-card-top" }, [
                            _createElementVNode("div", null, [
                              _createElementVNode("div", { class: "prod-name" }, "5G-A融合套餐299元"),
                              _createElementVNode("div", { class: "prod-code" }, "900102307")
                            ]),
                            _createElementVNode("div", { class: "health-score hs-green" }, "83")
                          ]),
                          _createElementVNode("span", { class: "prod-type pt-fusion" }, "融合"),
                          _createElementVNode("div", { class: "prod-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "5.8 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("新增"),
                              _createElementVNode("b", null, "0.68 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("环比"),
                              _createElementVNode("b", { style: {"color":"var(--red)"} }, "+9.6%")
                            ])
                          ])
                        ])
                      ])
                    ]),
                    _createCommentVNode(" ===== 单品健康度详情（点击后显示） ===== "),
                    _createElementVNode("div", {
                      id: "health-detail",
                      style: {"display":"none"}
                    }, [
                      _createElementVNode("div", {
                        class: "back-link",
                        onclick: "closeHealth()"
                      }, "→'返回 5G-A 产品列表"),
                      _createElementVNode("div", {
                        class: "card",
                        style: {"padding":"16px 20px"}
                      }, [
                        _createElementVNode("div", { style: {"display":"flex","justify-content":"space-between","align-items":"center","flex-wrap":"wrap","gap":"12px"} }, [
                          _createElementVNode("div", null, [
                            _createElementVNode("div", { style: {"font-size":"17px","font-weight":"700","display":"flex","align-items":"center","gap":"10px"} }, [
                              _createElementVNode("span", { id: "hd-name" }, "5G-A套餐159元"),
                              _createElementVNode("span", {
                                id: "hd-code",
                                style: {"font-size":"12px","font-weight":"400","color":"var(--text-lt)"}
                              }, "900102308"),
                              _createElementVNode("span", {
                                id: "hd-type",
                                class: "prod-type pt-single",
                                style: {"margin-bottom":"0"}
                              }, "单品")
                            ])
                          ]),
                          _createElementVNode("div", { style: {"display":"flex","gap":"8px"} }, [
                            _createElementVNode("button", {
                              class: "btn btn-sm btn-primary",
                              onclick: "opsOpenRules()"
                            }, "运营规则"),
                            _createElementVNode("button", { class: "btn btn-sm" }, "导出报表")
                          ])
                        ])
                      ]),
                      _createElementVNode("div", { class: "health-hero" }, [
                        _createElementVNode("div", { class: "hero-score hs-green" }, [
                          _createElementVNode("b", null, "88"),
                          _createElementVNode("span", null, "分")
                        ]),
                        _createElementVNode("div", { class: "hero-body" }, [
                          _createElementVNode("div", {
                            class: "hero-tagline",
                            style: {"background":"var(--green-bg)","color":"var(--green)"}
                          }, "健康 · 较上月'+4 分"),
                          _createElementVNode("div", { class: "hero-desc" }, "5G-A 网络驻留比持续提升，高值用户占比稳定；当月新增 1.82 万、新增占比?10.0% 保持稳健，续约率 91.5%、整体运营健康、"),
                          _createElementVNode("div", { class: "hero-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "12.5 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("当月新增"),
                              _createElementVNode("b", { style: {"color":"var(--green)"} }, "1.82 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("当月收入"),
                              _createElementVNode("b", null, "3,168 万")
                            ])
                          ])
                        ])
                      ]),
                      _createElementVNode("div", { class: "dim-grid" }, [
                        _createCommentVNode(" ①效益维度：赚了多少？赚得怎么样？ "),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", { class: "bar" }),
                              _createTextVNode("效益维度"),
                              _createElementVNode("span", { class: "q" }, "赚了多少 · 赚得怎么标")
                            ]),
                            _createElementVNode("div", { style: {"display":"flex","align-items":"center","gap":"12px"} }, [
                              _createElementVNode("div", {
                                class: "ops-tabs",
                                id: "opsEffectTabs"
                              }, [
                                _createElementVNode("button", {
                                  type: "button",
                                  class: "active",
                                  "data-effect": "income"
                                }, "收入趋势"),
                                _createElementVNode("button", {
                                  type: "button",
                                  "data-effect": "profit"
                                }, "利润率"),
                                _createElementVNode("button", {
                                  type: "button",
                                  "data-effect": "crm"
                                }, "营销费用占比")
                              ]),
                              _createElementVNode("div", { class: "ops-mod-score" }, [
                                _createTextVNode("40"),
                                _createElementVNode("small", null, " 分")
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 收入趋势：双轴'当月+累计) + 同'环比折线 "),
                          _createElementVNode("div", {
                            class: "ops-tabpane active",
                            id: "opsEffectIncomePane"
                          }, [
                            _createElementVNode("div", {
                              class: "ops-sub-toggle",
                              id: "opsEffectRateTabs"
                            }, [
                              _createElementVNode("button", {
                                type: "button",
                                class: "active",
                                "data-rate": "yoy"
                              }, "同比"),
                              _createElementVNode("button", {
                                type: "button",
                                "data-rate": "mom"
                              }, "环比")
                            ]),
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsEffectIncome",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"#1d8bff"}
                                }),
                                _createTextVNode("当月收入(万元)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"#0a4eb0"}
                                }),
                                _createTextVNode("累计收入(万元)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"#F59E0B"}
                                }),
                                _createTextVNode("当月收入同比/环比(%)")
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 利润率：折线 "),
                          _createElementVNode("div", {
                            class: "ops-tabpane",
                            id: "opsEffectProfitPane"
                          }, [
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsEffectProfit",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--blue)"}
                                }),
                                _createTextVNode("利润率?%)"),
                                _createElementVNode("b", { style: {"margin-left":"6px","color":"var(--text)"} }, "14.2%")
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 营销费用占比：折线?"),
                          _createElementVNode("div", {
                            class: "ops-tabpane",
                            id: "opsEffectCrmPane"
                          }, [
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsEffectCrm",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--yellow)"}
                                }),
                                _createTextVNode("营销费用占比(%)"),
                                _createElementVNode("b", { style: {"margin-left":"6px","color":"var(--text)"} }, "16.5%")
                              ])
                            ])
                          ])
                        ]),
                        _createCommentVNode(" ①市场维度：有多少于'· 增长怎么标'· 人从哪来，'"),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", {
                                class: "bar",
                                style: {"background":"var(--green)"}
                              }),
                              _createTextVNode("市场维度"),
                              _createElementVNode("span", { class: "q" }, "有多少人 · 增长怎么标?· 人从哪里板")
                            ]),
                            _createElementVNode("div", { class: "ops-mod-score" }, [
                              _createTextVNode("22"),
                              _createElementVNode("small", null, " 分")
                            ])
                          ]),
                          _createCommentVNode(" 左：用户 ，'右：渠道划分 "),
                          _createElementVNode("div", { class: "ops-mkt-2col" }, [
                            _createCommentVNode(" 用户区块 "),
                            _createElementVNode("div", { class: "ops-mkt-block ops-mkt-users" }, [
                              _createElementVNode("div", { class: "ops-mkt-block-head" }, [
                                _createElementVNode("div", { style: {"display":"flex","align-items":"center","gap":"10px","flex-wrap":"wrap"} }, [
                                  _createElementVNode("span", { class: "ops-mkt-block-title" }, "用户"),
                                  _createElementVNode("div", {
                                    class: "ops-tabs",
                                    id: "opsUserTabs"
                                  }, [
                                    _createElementVNode("button", {
                                      type: "button",
                                      class: "active",
                                      "data-utab": "scale"
                                    }, "规模"),
                                    _createElementVNode("button", {
                                      type: "button",
                                      "data-utab": "active"
                                    }, "活跃")
                                  ])
                                ])
                              ]),
                              _createCommentVNode(" 规模：双轴到达用户+月净增 + 同环比折线 "),
                              _createElementVNode("div", {
                                class: "ops-utabpane active",
                                id: "opsUserScalePane"
                              }, [
                                _createElementVNode("div", { class: "ops-rate-row" }, [
                                  _createElementVNode("span", { class: "ops-mkt-sub" }, "到达用户 · 月净增"),
                                  _createElementVNode("div", {
                                    class: "ops-sub-toggle",
                                    id: "opsGrowthRateTabs"
                                  }, [
                                    _createElementVNode("button", {
                                      type: "button",
                                      class: "active",
                                      "data-grate": "yoy"
                                    }, "同比"),
                                    _createElementVNode("button", {
                                      type: "button",
                                      "data-grate": "mom"
                                    }, "环比")
                                  ])
                                ]),
                                _createElementVNode("div", {
                                  class: "ops-chartbox",
                                  id: "opsMktScale",
                                  style: {"height":"195px"}
                                }),
                                _createElementVNode("div", { class: "ops-legend" }, [
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"#1d8bff"}
                                    }),
                                    _createTextVNode("到达用户(户)")
                                  ]),
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"#0a4eb0"}
                                    }),
                                    _createTextVNode("月净增(户)")
                                  ]),
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"#F59E0B"}
                                    }),
                                    _createTextVNode("同?环比(%)")
                                  ])
                                ])
                              ]),
                              _createCommentVNode(" 活跃：活跃用户率 + 付费活跃率'双折线'"),
                              _createElementVNode("div", {
                                class: "ops-utabpane",
                                id: "opsUserActivePane"
                              }, [
                                _createElementVNode("div", {
                                  class: "ops-chartbox",
                                  id: "opsActiveTrend",
                                  style: {"height":"195px"}
                                }),
                                _createElementVNode("div", { class: "ops-legend" }, [
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"var(--green)"}
                                    }),
                                    _createTextVNode("活跃用户率(%)")
                                  ]),
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"var(--blue)"}
                                    }),
                                    _createTextVNode("付费活跃率(%)")
                                  ])
                                ])
                              ])
                            ]),
                            _createCommentVNode(" 渠道划分区块 "),
                            _createElementVNode("div", { class: "ops-mkt-block ops-mkt-channel" }, [
                              _createElementVNode("div", { class: "ops-mkt-block-head" }, [
                                _createElementVNode("span", { class: "ops-mkt-block-title" }, "渠道划分"),
                                _createElementVNode("select", {
                                  class: "ops-mkt-month",
                                  id: "opsChannelMonth"
                                }, [
                                  _createElementVNode("option", { value: "2025-12" }, "2025年12月"),
                                  _createElementVNode("option", { value: "2026-01" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-02" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-03" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-04" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-05" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-06" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-07" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-08" }, "2026年1月"),
                                  _createElementVNode("option", {
                                    value: "2026-09",
                                    selected: ""
                                  }, "2026年1月")
                                ])
                              ]),
                              _createElementVNode("div", {
                                class: "ops-chartbox",
                                id: "opsChannelPie",
                                style: {"height":"245px"}
                              })
                            ])
                          ])
                        ]),
                        _createCommentVNode(" ①运营质量维度：运营有没有问题？哪里有问题，?"),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", {
                                class: "bar",
                                style: {"background":"var(--yellow)"}
                              }),
                              _createTextVNode("运营质量维度"),
                              _createElementVNode("span", { class: "q" }, "运营有没有问预'· 哪里有问预")
                            ]),
                            _createElementVNode("div", { style: {"display":"flex","align-items":"center","gap":"12px"} }, [
                              _createElementVNode("div", {
                                class: "ops-tabs",
                                id: "opsQualityTabs"
                              }, [
                                _createElementVNode("button", {
                                  type: "button",
                                  class: "active",
                                  "data-qt": "retain"
                                }, "留存"),
                                _createElementVNode("button", {
                                  type: "button",
                                  "data-qt": "service"
                                }, "服务")
                              ]),
                              _createElementVNode("div", { class: "ops-mod-score" }, [
                                _createTextVNode("18"),
                                _createElementVNode("small", null, " 分")
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 留存：T+1活跃率?/ T+3 / T+6 退订率 "),
                          _createElementVNode("div", {
                            class: "ops-tabpane active",
                            id: "opsQRetainPane"
                          }, [
                            _createElementVNode("div", { class: "ops-q-chips" }, [
                              _createElementVNode("div", { class: "ops-q-chip c-green" }, [
                                _createElementVNode("span", { class: "q-ico" }, "📈"),
                                _createElementVNode("span", null, "T+1 活跃率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "78.4%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.2")
                                ]),
                                _createElementVNode("em", null, "新增次日仍活跃")
                              ]),
                              _createElementVNode("div", { class: "ops-q-chip c-yellow" }, [
                                _createElementVNode("span", { class: "q-ico" }, "📉"),
                                _createElementVNode("span", null, "T+3 退订率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "1.40%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.06")
                                ]),
                                _createElementVNode("em", null, "新增 3 日退订")
                              ]),
                              _createElementVNode("div", { class: "ops-q-chip c-blue" }, [
                                _createElementVNode("span", { class: "q-ico" }, "📉"),
                                _createElementVNode("span", null, "T+6 退订率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "2.30%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.06")
                                ]),
                                _createElementVNode("em", null, "新增 6 日退订")
                              ])
                            ]),
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsQRetain",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--green)"}
                                }),
                                _createTextVNode("T+1活跃率?%)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--yellow)"}
                                }),
                                _createTextVNode("T+3退订率(%)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--blue)"}
                                }),
                                _createTextVNode("T+6退订率(%)")
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 服务：资费投诉率 / 业务办理成功率'"),
                          _createElementVNode("div", {
                            class: "ops-tabpane",
                            id: "opsQServicePane"
                          }, [
                            _createElementVNode("div", { class: "ops-q-chips" }, [
                              _createElementVNode("div", { class: "ops-q-chip c-blue" }, [
                                _createElementVNode("span", { class: "q-ico" }, "，"),
                                _createElementVNode("span", null, "业务办理成功率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "99.2%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.1")
                                ]),
                                _createElementVNode("em", null, "高于 98% 目标线")
                              ]),
                              _createElementVNode("div", { class: "ops-q-chip c-red" }, [
                                _createElementVNode("span", { class: "q-ico" }, "⚠️"),
                                _createElementVNode("span", null, "资费投诉率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "0.02%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▼")
                                ]),
                                _createElementVNode("em", null, "远低于?0.1% 预警线")
                              ])
                            ]),
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsQService",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--blue)"}
                                }),
                                _createTextVNode("业务办理成功率(%)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--red)"}
                                }),
                                _createTextVNode("资费投诉率(%)")
                              ])
                            ])
                          ])
                        ]),
                        _createCommentVNode(" ①生命周期维度：用户处在哪个阶段？新增质量怎么样？ "),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", {
                                class: "bar",
                                style: {"background":"var(--purple)"}
                              }),
                              _createTextVNode("生命周期维度"),
                              _createElementVNode("span", { class: "q" }, "用户处在哪个阶段 · 新增质量怎么标")
                            ]),
                            _createElementVNode("div", { class: "ops-mod-score" }, [
                              _createTextVNode("8"),
                              _createElementVNode("small", null, " 分")
                            ])
                          ]),
                          _createCommentVNode(" 用户生命线：各阶段表率'+ 占比 "),
                          _createElementVNode("div", { class: "ops-life" }, [
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-new" }, "新增"),
                              _createElementVNode("div", { class: "ln-ico c-blue" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z" }),
                                  _createElementVNode("path", { d: "M2 19a6 6 0 0 1 10-4.2" }),
                                  _createElementVNode("path", { d: "M19 8v6M16 11h6" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "新增用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("10.0"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--blue)"} }),
                                _createTextVNode("本月新增占比")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-act" }, "激活"),
                              _createElementVNode("div", { class: "ln-ico c-green" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M13 2 3 14h7l-1 8 10-12h-7l1-8z" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "活跃用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("86.4"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--green)"} }),
                                _createTextVNode("月活跃率")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-ret" }, "留存"),
                              _createElementVNode("div", { class: "ln-ico c-purple" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z" }),
                                  _createElementVNode("path", { d: "m9 12 2 2 4-4" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "留存用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("97.7"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--purple)"} }),
                                _createTextVNode("T+6 留存")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-sil" }, "沉默"),
                              _createElementVNode("div", { class: "ln-ico c-amber" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "沉默用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("8.5"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"#D48806"} }),
                                _createTextVNode("沉默占比")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-churn" }, "流失 · 续约"),
                              _createElementVNode("div", { class: "ln-ico c-red" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M21 12a9 9 0 1 1-2.9-6.6" }),
                                  _createElementVNode("path", { d: "M21 3v5h-5" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "续约率"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("91.5"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--red)"} }),
                                _createTextVNode("到期续约率")
                              ])
                            ])
                          ]),
                          _createCommentVNode(" 阶段占比流量图?"),
                          _createElementVNode("div", { class: "ops-flow" }, [
                            _createElementVNode("div", { class: "ops-flow-head" }, [
                              _createElementVNode("div", { class: "ops-flow-title" }, [
                                _createElementVNode("i"),
                                _createTextVNode("存量结构 · 阶段占比")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-flow-track" }, [
                              _createElementVNode("div", {
                                class: "ops-flow-seg",
                                style: {"width":"86.4%","background":"linear-gradient(90deg,#00A86C,#00B578)"}
                              }, [
                                _createTextVNode("活跃用户"),
                                _createElementVNode("small", null, "86.4%")
                              ]),
                              _createElementVNode("div", {
                                class: "ops-flow-seg",
                                style: {"width":"8.5%","background":"linear-gradient(90deg,#C07A06,#D48806)"}
                              }, [
                                _createTextVNode("沉默"),
                                _createElementVNode("small", null, "8.5%")
                              ]),
                              _createElementVNode("div", {
                                class: "ops-flow-seg",
                                style: {"width":"5.1%","background":"linear-gradient(90deg,#D91B27,#F5222D)"}
                              }, [
                                _createTextVNode("流失"),
                                _createElementVNode("small", null, "5.1%")
                              ])
                            ])
                          ])
                        ])
                      ]),
                      _createElementVNode("div", { class: "card" }, [
                        _createElementVNode("div", { class: "card-head" }, [
                          _createElementVNode("div", {
                            class: "card-title",
                            style: {"font-size":"13px"}
                          }, [
                            _createElementVNode("span", { style: {"width":"3px","height":"14px","background":"var(--red)","border-radius":"2px","display":"inline-block"} }),
                            _createTextVNode("异动预警")
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "alert alert-green",
                          style: {"margin-bottom":"0"}
                        }, [
                          _createElementVNode("div", { class: "alert-icon" }, "，"),
                          _createElementVNode("div", { class: "alert-text" }, [
                            _createElementVNode("b", null, "存量基本盘稳定："),
                            _createTextVNode("投诉率?0.02%、T+6 退订率 2.30%、业务办理成功率 99.2%、续约率 91.5% 均在正常范围")
                          ])
                        ])
                      ])
                    ]),
                    _createCommentVNode(" ===== 5G-A套餐单品299元?健康度详情（亚健康画像） ===== "),
                    _createElementVNode("div", {
                      id: "health-detail-299",
                      style: {"display":"none"}
                    }, [
                      _createElementVNode("div", {
                        class: "back-link",
                        onclick: "closeHealth()"
                      }, "→?返回 5G-A 产品列表"),
                      _createElementVNode("div", {
                        class: "card",
                        style: {"padding":"16px 20px"}
                      }, [
                        _createElementVNode("div", { style: {"display":"flex","justify-content":"space-between","align-items":"center","flex-wrap":"wrap","gap":"12px"} }, [
                          _createElementVNode("div", null, [
                            _createElementVNode("div", { style: {"font-size":"17px","font-weight":"700","display":"flex","align-items":"center","gap":"10px"} }, [
                              _createElementVNode("span", { id: "hd-name-299" }, "5G-A套餐单品299元"),
                              _createElementVNode("span", {
                                id: "hd-code-299",
                                style: {"font-size":"12px","font-weight":"400","color":"var(--text-lt)"}
                              }, "900102310"),
                              _createElementVNode("span", {
                                id: "hd-type-299",
                                class: "prod-type pt-single",
                                style: {"margin-bottom":"0"}
                              }, "单品")
                            ])
                          ]),
                          _createElementVNode("div", { style: {"display":"flex","gap":"8px"} }, [
                            _createElementVNode("button", { class: "btn btn-sm" }, "导出报表")
                          ])
                        ])
                      ]),
                      _createElementVNode("div", { class: "health-hero" }, [
                        _createElementVNode("div", { class: "hero-score hs-yellow" }, [
                          _createElementVNode("b", null, "79"),
                          _createElementVNode("span", null, "分")
                        ]),
                        _createElementVNode("div", { class: "hero-body" }, [
                          _createElementVNode("div", {
                            class: "hero-tagline",
                            style: {"background":"var(--yellow-bg)","color":"#D48806"}
                          }, "亚健康'· 较上月'-3 分"),
                          _createElementVNode("div", { class: "hero-desc" }, "高端档位用户增长放缓，收入环比连续两月下滑（-2.8%），利润率降自?11.6% 而营销费用占比升至 18.9%，续约率 88.6% 偏低、沉默用户占比?14.2% 偏高，需重点维系高值存量用户并加大权益焕活、"),
                          _createElementVNode("div", { class: "hero-metrics" }, [
                            _createElementVNode("div", null, [
                              _createTextVNode("到达用户"),
                              _createElementVNode("b", null, "4.2 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("当月新增"),
                              _createElementVNode("b", { style: {"color":"var(--green)"} }, "0.42 万")
                            ]),
                            _createElementVNode("div", null, [
                              _createTextVNode("当月收入"),
                              _createElementVNode("b", null, "1,246 万")
                            ])
                          ])
                        ])
                      ]),
                      _createElementVNode("div", { class: "dim-grid" }, [
                        _createCommentVNode(" ①效益维度 "),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", { class: "bar" }),
                              _createTextVNode("效益维度"),
                              _createElementVNode("span", { class: "q" }, "赚了多少 · 赚得怎么标")
                            ]),
                            _createElementVNode("div", { style: {"display":"flex","align-items":"center","gap":"12px"} }, [
                              _createElementVNode("div", {
                                class: "ops-tabs",
                                id: "opsEffectTabs299"
                              }, [
                                _createElementVNode("button", {
                                  type: "button",
                                  class: "active",
                                  "data-effect": "income"
                                }, "收入趋势"),
                                _createElementVNode("button", {
                                  type: "button",
                                  "data-effect": "profit"
                                }, "利润率"),
                                _createElementVNode("button", {
                                  type: "button",
                                  "data-effect": "crm"
                                }, "营销费用占比")
                              ]),
                              _createElementVNode("div", { class: "ops-mod-score" }, [
                                _createTextVNode("33"),
                                _createElementVNode("small", null, " 分")
                              ])
                            ])
                          ]),
                          _createElementVNode("div", {
                            class: "ops-tabpane active",
                            id: "opsEffectIncomePane299"
                          }, [
                            _createElementVNode("div", {
                              class: "ops-sub-toggle",
                              id: "opsEffectRateTabs299"
                            }, [
                              _createElementVNode("button", {
                                type: "button",
                                class: "active",
                                "data-rate": "yoy"
                              }, "同比"),
                              _createElementVNode("button", {
                                type: "button",
                                "data-rate": "mom"
                              }, "环比")
                            ]),
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsEffectIncome299",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"#1d8bff"}
                                }),
                                _createTextVNode("当月收入(万元)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"#0a4eb0"}
                                }),
                                _createTextVNode("累计收入(万元)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"#F59E0B"}
                                }),
                                _createTextVNode("当月收入同比/环比(%)")
                              ])
                            ])
                          ]),
                          _createElementVNode("div", {
                            class: "ops-tabpane",
                            id: "opsEffectProfitPane299"
                          }, [
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsEffectProfit299",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--blue)"}
                                }),
                                _createTextVNode("利润率?%)"),
                                _createElementVNode("b", { style: {"margin-left":"6px","color":"var(--text)"} }, "11.6%")
                              ])
                            ])
                          ]),
                          _createElementVNode("div", {
                            class: "ops-tabpane",
                            id: "opsEffectCrmPane299"
                          }, [
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsEffectCrm299",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--yellow)"}
                                }),
                                _createTextVNode("营销费用占比(%)"),
                                _createElementVNode("b", { style: {"margin-left":"6px","color":"var(--text)"} }, "18.9%")
                              ])
                            ])
                          ])
                        ]),
                        _createCommentVNode(" ①市场维度 "),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", {
                                class: "bar",
                                style: {"background":"var(--green)"}
                              }),
                              _createTextVNode("市场维度"),
                              _createElementVNode("span", { class: "q" }, "有多少人 · 增长怎么标?· 人从哪里板")
                            ]),
                            _createElementVNode("div", { class: "ops-mod-score" }, [
                              _createTextVNode("19"),
                              _createElementVNode("small", null, " 分")
                            ])
                          ]),
                          _createElementVNode("div", { class: "ops-mkt-2col" }, [
                            _createElementVNode("div", { class: "ops-mkt-block ops-mkt-users" }, [
                              _createElementVNode("div", { class: "ops-mkt-block-head" }, [
                                _createElementVNode("div", { style: {"display":"flex","align-items":"center","gap":"10px","flex-wrap":"wrap"} }, [
                                  _createElementVNode("span", { class: "ops-mkt-block-title" }, "用户"),
                                  _createElementVNode("div", {
                                    class: "ops-tabs",
                                    id: "opsUserTabs299"
                                  }, [
                                    _createElementVNode("button", {
                                      type: "button",
                                      class: "active",
                                      "data-utab": "scale"
                                    }, "规模"),
                                    _createElementVNode("button", {
                                      type: "button",
                                      "data-utab": "active"
                                    }, "活跃")
                                  ])
                                ])
                              ]),
                              _createElementVNode("div", {
                                class: "ops-utabpane active",
                                id: "opsUserScalePane299"
                              }, [
                                _createElementVNode("div", { class: "ops-rate-row" }, [
                                  _createElementVNode("span", { class: "ops-mkt-sub" }, "到达用户 · 月净增"),
                                  _createElementVNode("div", {
                                    class: "ops-sub-toggle",
                                    id: "opsGrowthRateTabs299"
                                  }, [
                                    _createElementVNode("button", {
                                      type: "button",
                                      class: "active",
                                      "data-grate": "yoy"
                                    }, "同比"),
                                    _createElementVNode("button", {
                                      type: "button",
                                      "data-grate": "mom"
                                    }, "环比")
                                  ])
                                ]),
                                _createElementVNode("div", {
                                  class: "ops-chartbox",
                                  id: "opsMktScale299",
                                  style: {"height":"195px"}
                                }),
                                _createElementVNode("div", { class: "ops-legend" }, [
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"#1d8bff"}
                                    }),
                                    _createTextVNode("到达用户(户)")
                                  ]),
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"#0a4eb0"}
                                    }),
                                    _createTextVNode("月净增(户)")
                                  ]),
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"#F59E0B"}
                                    }),
                                    _createTextVNode("同?环比(%)")
                                  ])
                                ])
                              ]),
                              _createElementVNode("div", {
                                class: "ops-utabpane",
                                id: "opsUserActivePane299"
                              }, [
                                _createElementVNode("div", {
                                  class: "ops-chartbox",
                                  id: "opsActiveTrend299",
                                  style: {"height":"195px"}
                                }),
                                _createElementVNode("div", { class: "ops-legend" }, [
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"var(--green)"}
                                    }),
                                    _createTextVNode("活跃用户率(%)")
                                  ]),
                                  _createElementVNode("span", null, [
                                    _createElementVNode("i", {
                                      class: "ops-dot",
                                      style: {"background":"var(--blue)"}
                                    }),
                                    _createTextVNode("付费活跃率(%)")
                                  ])
                                ])
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-mkt-block ops-mkt-channel" }, [
                              _createElementVNode("div", { class: "ops-mkt-block-head" }, [
                                _createElementVNode("span", { class: "ops-mkt-block-title" }, "渠道划分"),
                                _createElementVNode("select", {
                                  class: "ops-mkt-month",
                                  id: "opsChannelMonth299"
                                }, [
                                  _createElementVNode("option", { value: "2025-12" }, "2025年12月"),
                                  _createElementVNode("option", { value: "2026-01" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-02" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-03" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-04" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-05" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-06" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-07" }, "2026年1月"),
                                  _createElementVNode("option", { value: "2026-08" }, "2026年1月"),
                                  _createElementVNode("option", {
                                    value: "2026-09",
                                    selected: ""
                                  }, "2026年1月")
                                ])
                              ]),
                              _createElementVNode("div", {
                                class: "ops-chartbox",
                                id: "opsChannelPie299",
                                style: {"height":"245px"}
                              })
                            ])
                          ])
                        ]),
                        _createCommentVNode(" ①运营质量维度 "),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", {
                                class: "bar",
                                style: {"background":"var(--yellow)"}
                              }),
                              _createTextVNode("运营质量维度"),
                              _createElementVNode("span", { class: "q" }, "运营有没有问预'· 哪里有问预")
                            ]),
                            _createElementVNode("div", { style: {"display":"flex","align-items":"center","gap":"12px"} }, [
                              _createElementVNode("div", {
                                class: "ops-tabs",
                                id: "opsQualityTabs299"
                              }, [
                                _createElementVNode("button", {
                                  type: "button",
                                  class: "active",
                                  "data-qt": "retain"
                                }, "留存"),
                                _createElementVNode("button", {
                                  type: "button",
                                  "data-qt": "service"
                                }, "服务")
                              ]),
                              _createElementVNode("div", { class: "ops-mod-score" }, [
                                _createTextVNode("17"),
                                _createElementVNode("small", null, " 分")
                              ])
                            ])
                          ]),
                          _createElementVNode("div", {
                            class: "ops-tabpane active",
                            id: "opsQRetainPane299"
                          }, [
                            _createElementVNode("div", { class: "ops-q-chips" }, [
                              _createElementVNode("div", { class: "ops-q-chip c-green" }, [
                                _createElementVNode("span", { class: "q-ico" }, "📈"),
                                _createElementVNode("span", null, "T+1 活跃率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "76.1%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.3")
                                ]),
                                _createElementVNode("em", null, "新增次日仍活跃")
                              ]),
                              _createElementVNode("div", { class: "ops-q-chip c-red" }, [
                                _createElementVNode("span", { class: "q-ico" }, "⚠️"),
                                _createElementVNode("span", null, "T+3 退订率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "2.10%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.03")
                                ]),
                                _createElementVNode("em", null, "新增 3 日退订")
                              ]),
                              _createElementVNode("div", { class: "ops-q-chip c-yellow" }, [
                                _createElementVNode("span", { class: "q-ico" }, "⚠️"),
                                _createElementVNode("span", null, "T+6 退订率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "3.20%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.02")
                                ]),
                                _createElementVNode("em", null, "新增 6 日退订")
                              ])
                            ]),
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsQRetain299",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--green)"}
                                }),
                                _createTextVNode("T+1活跃率?%)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--yellow)"}
                                }),
                                _createTextVNode("T+3退订率(%)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--blue)"}
                                }),
                                _createTextVNode("T+6退订率(%)")
                              ])
                            ])
                          ]),
                          _createElementVNode("div", {
                            class: "ops-tabpane",
                            id: "opsQServicePane299"
                          }, [
                            _createElementVNode("div", { class: "ops-q-chips" }, [
                              _createElementVNode("div", { class: "ops-q-chip c-yellow" }, [
                                _createElementVNode("span", { class: "q-ico" }, "⚠️"),
                                _createElementVNode("span", null, "业务办理成功率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "98.4%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▲.1")
                                ]),
                                _createElementVNode("em", null, "略低于099% 目标")
                              ]),
                              _createElementVNode("div", { class: "ops-q-chip c-red" }, [
                                _createElementVNode("span", { class: "q-ico" }, "⚠️"),
                                _createElementVNode("span", null, "资费投诉率"),
                                _createElementVNode("div", { class: "q-row" }, [
                                  _createElementVNode("b", null, "0.04%"),
                                  _createElementVNode("span", { class: "q-trend" }, "▼")
                                ]),
                                _createElementVNode("em", null, "接近 0.05% 预警")
                              ])
                            ]),
                            _createElementVNode("div", {
                              class: "ops-chartbox",
                              id: "opsQService299",
                              style: {"height":"225px"}
                            }),
                            _createElementVNode("div", { class: "ops-legend" }, [
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--blue)"}
                                }),
                                _createTextVNode("业务办理成功率(%)")
                              ]),
                              _createElementVNode("span", null, [
                                _createElementVNode("i", {
                                  class: "ops-dot",
                                  style: {"background":"var(--red)"}
                                }),
                                _createTextVNode("资费投诉率(%)")
                              ])
                            ])
                          ])
                        ]),
                        _createCommentVNode(" ①生命周期维度 "),
                        _createElementVNode("div", { class: "ops-mod" }, [
                          _createElementVNode("div", { class: "ops-mod-head" }, [
                            _createElementVNode("div", { class: "ops-mod-title" }, [
                              _createElementVNode("span", {
                                class: "bar",
                                style: {"background":"var(--purple)"}
                              }),
                              _createTextVNode("生命周期维度"),
                              _createElementVNode("span", { class: "q" }, "用户处在哪个阶段 · 新增质量怎么标")
                            ]),
                            _createElementVNode("div", { class: "ops-mod-score" }, [
                              _createTextVNode("6"),
                              _createElementVNode("small", null, " 分")
                            ])
                          ]),
                          _createElementVNode("div", { class: "ops-life" }, [
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-new" }, "新增"),
                              _createElementVNode("div", { class: "ln-ico c-blue" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z" }),
                                  _createElementVNode("path", { d: "M2 19a6 6 0 0 1 10-4.2" }),
                                  _createElementVNode("path", { d: "M19 8v6M16 11h6" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "新增用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("10.5"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--blue)"} }),
                                _createTextVNode("本月新增占比")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-act" }, "激活"),
                              _createElementVNode("div", { class: "ln-ico c-green" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M13 2 3 14h7l-1 8 10-12h-7l1-8z" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "活跃用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("80.5"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--green)"} }),
                                _createTextVNode("月活跃率")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-ret" }, "留存"),
                              _createElementVNode("div", { class: "ln-ico c-purple" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z" }),
                                  _createElementVNode("path", { d: "m9 12 2 2 4-4" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "留存用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("96.8"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--purple)"} }),
                                _createTextVNode("T+6 留存")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-sil" }, "沉默"),
                              _createElementVNode("div", { class: "ln-ico c-amber" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "沉默用户"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("14.2"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"#D48806"} }),
                                _createTextVNode("沉默占比")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-life-node" }, [
                              _createElementVNode("span", { class: "ops-tag ln-t-churn" }, "流失 · 续约"),
                              _createElementVNode("div", { class: "ln-ico c-red" }, [
                                (_openBlock(), _createElementBlock("svg", {
                                  viewBox: "0 0 24 24",
                                  fill: "none",
                                  "stroke-width": "1.8",
                                  "stroke-linecap": "round",
                                  "stroke-linejoin": "round"
                                }, [
                                  _createElementVNode("path", { d: "M21 12a9 9 0 1 1-2.9-6.6" }),
                                  _createElementVNode("path", { d: "M21 3v5h-5" })
                                ]))
                              ]),
                              _createElementVNode("div", { class: "ln-name" }, "续约率"),
                              _createElementVNode("div", { class: "ln-val" }, [
                                _createTextVNode("88.6"),
                                _createElementVNode("small", null, "%")
                              ]),
                              _createElementVNode("div", { class: "ln-pct" }, [
                                _createElementVNode("i", { style: {"background":"var(--red)"} }),
                                _createTextVNode("到期续约率")
                              ])
                            ])
                          ]),
                          _createElementVNode("div", { class: "ops-flow" }, [
                            _createElementVNode("div", { class: "ops-flow-head" }, [
                              _createElementVNode("div", { class: "ops-flow-title" }, [
                                _createElementVNode("i"),
                                _createTextVNode("存量结构 · 阶段占比")
                              ])
                            ]),
                            _createElementVNode("div", { class: "ops-flow-track" }, [
                              _createElementVNode("div", {
                                class: "ops-flow-seg",
                                style: {"width":"80.5%","background":"linear-gradient(90deg,#00A86C,#00B578)"}
                              }, [
                                _createTextVNode("活跃用户"),
                                _createElementVNode("small", null, "80.5%")
                              ]),
                              _createElementVNode("div", {
                                class: "ops-flow-seg",
                                style: {"width":"14.2%","background":"linear-gradient(90deg,#C07A06,#D48806)"}
                              }, [
                                _createTextVNode("沉默"),
                                _createElementVNode("small", null, "14.2%")
                              ]),
                              _createElementVNode("div", {
                                class: "ops-flow-seg",
                                style: {"width":"5.3%","background":"linear-gradient(90deg,#D91B27,#F5222D)"}
                              }, [
                                _createTextVNode("流失"),
                                _createElementVNode("small", null, "5.3%")
                              ])
                            ])
                          ])
                        ])
                      ]),
                      _createElementVNode("div", { class: "card" }, [
                        _createElementVNode("div", { class: "card-head" }, [
                          _createElementVNode("div", {
                            class: "card-title",
                            style: {"font-size":"13px"}
                          }, [
                            _createElementVNode("span", { style: {"width":"3px","height":"14px","background":"var(--red)","border-radius":"2px","display":"inline-block"} }),
                            _createTextVNode("异动预警")
                          ])
                        ]),
                        _createElementVNode("div", {
                          class: "alert alert-yellow",
                          style: {"margin-bottom":"0"}
                        }, [
                          _createElementVNode("div", { class: "alert-icon" }, "!"),
                          _createElementVNode("div", { class: "alert-text" }, [
                            _createElementVNode("b", null, "收入连续两月环比下滑，32.8%，"),
                            _createTextVNode("，续约率 88.6% 一?T+6 退订率 3.20% 均偏离健康线、沉默用户占比?14.2% 偏高，建议针对到期用户开展权益加码维系并加大沉默用户唤醒")
                          ])
                        ])
                      ])
                    ]),
                    _createCommentVNode(" 运营规则：通用经营指标·预警规则·推送方式（从单产品视图移入，?"),
                    _createElementVNode("div", {
                      id: "ops-rules-panel",
                      style: {"display":"none"}
                    }, [
                      _createElementVNode("div", {
                        class: "back-link",
                        onclick: "opsCloseRules()"
                      }, "→?返回单产品画像"),
                      _createElementVNode("div", { class: "ops-rules-bar" }, [
                        _createElementVNode("span", { class: "ops-rules-title" }, "运营规则 · 通用经营指标 · 预警规则 · 推送方式")
                      ]),
                      _createElementVNode("div", { class: "card" }, [
                        _createElementVNode("div", { class: "card-head" }, [
                          _createElementVNode("div", { class: "card-title" }, "通用经营指标 · 预警规则 · 推送方式")
                        ]),
                        _createElementVNode("div", { style: {"display":"flex","gap":"14px","flex-wrap":"wrap","align-items":"center","font-size":"11.5px","color":"var(--text-sub)","padding":"10px 14px","background":"#F7F9FC","border-radius":"6px","margin-bottom":"12px"} }, [
                          _createElementVNode("b", { style: {"color":"var(--text)","font-size":"12px"} }, "预警机制"),
                          _createElementVNode("span", { class: "risk-pill risk-high" }, [
                            _createElementVNode("span", { class: "lv" }, "阀"),
                            _createElementVNode("span", { class: "th" }, "达到阈值即触发")
                          ]),
                          _createElementVNode("span", { style: {"display":"flex","align-items":"center","gap":"5px"} }, [
                            _createElementVNode("i", { style: {"width":"9px","height":"9px","border-radius":"2px","background":"var(--purple)","display":"inline-block"} }),
                            _createTextVNode("异常推送（日调度）")
                          ])
                        ]),
                        _createElementVNode("div", { class: "table-responsive" }, [
                          _createElementVNode("table", { class: "metric-table" }, [
                            _createElementVNode("thead", null, [
                              _createElementVNode("tr", null, [
                                _createElementVNode("th", { style: {"width":"108px"} }, "指标类型"),
                                _createElementVNode("th", { style: {"width":"116px"} }, "指标名称"),
                                _createElementVNode("th", { style: {"width":"320px"} }, "预警规则"),
                                _createElementVNode("th", null, "指标口径")
                              ])
                            ]),
                            _createElementVNode("tbody", null, [
                              _createCommentVNode(" 效益维度：收入与价值类 "),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", {
                                  class: "m-module",
                                  rowspan: "5"
                                }, "效益维度"),
                                _createElementVNode("td", { class: "m-name" }, "当月收入"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "环比→'gt;10%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "该套餐当月出账收入（报表口径，")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "累计收入"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "环比→?gt;10%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "该套餐当年1 月至统计月累计出账收入")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "收入同'环比"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "下滑>5% 或连续两月下滑")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "（本月收入 - 上月收入）÷ 上月收入（或同比口径，")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "利润率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 12%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "（收入 - 营销费用 - 成本）÷ 收入")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "营销费用占比"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "高于 18%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "该套餐营销费用 ÷ 该套餐收入")
                              ]),
                              _createCommentVNode(" 市场维度：规模、活跃与渠道 "),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", {
                                  class: "m-module",
                                  rowspan: "5"
                                }, "市场维度"),
                                _createElementVNode("td", { class: "m-name" }, "到达用户"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "环比→'gt;5%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "当月出账且状态正常的该套餐主卡用户数（到达用户），副卡不重复计算")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "月净增用户"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "连续 3 日为费")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "当月新增 - 当月退订")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "活跃用户率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 60%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "当月有使用行为的该套餐用户?÷ 到达用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "付费活跃率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 5%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "付费活跃用户（当月产生实际计费账单）÷ 到达用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "渠道结构占比"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "单一渠道占比>70%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "线下营业原?/ 中国电信线上渠道 / 10000热线 / 安徽电信线上渠道 到达用户占比")
                              ]),
                              _createCommentVNode(" 运营质量维度：质量与留存 "),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", {
                                  class: "m-module",
                                  rowspan: "5"
                                }, "运营质量维度"),
                                _createElementVNode("td", { class: "m-name" }, "T+1 活跃率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 70%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "订购次日内仍活跃的新增用户'÷ 当期新增用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "T+3 退订率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "高于 3%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "订购同?3 天内退订用户?÷ 当期新增用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "T+6 退订率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "高于 10%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "订购同06 天内退订用户'÷ 当期新增用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "业务办理成功率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 98%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "该套餐受理成功笔数?÷ 受理总笔数")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "资费投诉率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "高于 0.1%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "涉及该套餐资费的投诉工单数'÷ 到达用户")
                              ]),
                              _createCommentVNode(" 生命周期维度：新增、留存与用户结构 "),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", {
                                  class: "m-module",
                                  rowspan: "5"
                                }, "生命周期维度"),
                                _createElementVNode("td", { class: "m-name" }, "新用户吸引占比"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 8%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "新入网用户中订购该套餐的人数占比")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "活跃用户占比"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 60%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "存量用户中当月为活跃阶段的占比（月活跃率，")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "T+6 留存率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 95%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "订购后第 6 天仍保持套餐状态的新增用户 ÷ 当期新增用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "沉默用户占比"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "高于 10%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "当月无任何使用行为的用户 ÷ 到达用户")
                              ]),
                              _createElementVNode("tr", null, [
                                _createElementVNode("td", { class: "m-name" }, "续约率"),
                                _createElementVNode("td", { class: "m-rule" }, [
                                  _createElementVNode("span", { class: "risk-pill risk-high" }, [
                                    _createElementVNode("span", { class: "lv" }, "阀"),
                                    _createElementVNode("span", { class: "th" }, "低于 90%")
                                  ])
                                ]),
                                _createElementVNode("td", { class: "m-desc" }, "到期用户中成功续约该套餐的用户占比")
                              ])
                            ])
                          ])
                        ])
                      ])
                    ])
                  ])
                ])
              ])
            ])
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'monitor']
        ]),
        _withDirectives(_createElementVNode("div", { class: "tab-content phone-preview-tab" }, [
          _createElementVNode("div", { class: "phone-frame" }, [
            _createCommentVNode(" 手机状态栏 "),
            _createElementVNode("div", { class: "phone-statusbar" }, [
              _createElementVNode("span", { class: "phone-time" }, _toDisplayString(phoneTime), 1 /* TEXT */),
              _createElementVNode("div", { class: "phone-status-icons" }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "14",
                  height: "10",
                  viewBox: "0 0 24 18",
                  fill: "currentColor"
                }, [
                  _createElementVNode("path", { d: "M1 14h2v6H1zM6 11h2v9H6zM11 8h2v12h-2zM16 5h2v15h-2zM21 2h2v18h-2z" })
                ])),
                (_openBlock(), _createElementBlock("svg", {
                  width: "14",
                  height: "10",
                  viewBox: "0 0 24 18",
                  fill: "currentColor"
                }, [
                  _createElementVNode("path", { d: "M12 3C7 3 2.7 5.1 0 8.5l2 2.5C4.2 8.3 7.9 6.5 12 6.5s7.8 1.8 10 4.5l2-2.5C21.3 5.1 17 3 12 3z" })
                ])),
                _createElementVNode("div", { class: "phone-battery" }, [
                  _createElementVNode("div", { class: "phone-battery-bar" }),
                  _createElementVNode("div", { class: "phone-battery-cap" })
                ])
              ])
            ]),
            _createCommentVNode(" APP标题 "),
            _createElementVNode("div", { class: "phone-navbar" }, [
              (_openBlock(), _createElementBlock("svg", {
                class: "phone-nav-back",
                width: "20",
                height: "20",
                viewBox: "0 0 24 24",
                fill: "none",
                stroke: "currentColor",
                "stroke-width": "2"
              }, [
                _createElementVNode("polyline", { points: "15 18 9 12 15 6" })
              ])),
              _createElementVNode("span", { class: "phone-nav-title" }, _toDisplayString(phoneNavTitle), 1 /* TEXT */),
              (_openBlock(), _createElementBlock("svg", {
                class: "phone-nav-more",
                width: "20",
                height: "20",
                viewBox: "0 0 24 24",
                fill: "currentColor"
              }, [
                _createElementVNode("circle", {
                  cx: "12",
                  cy: "5",
                  r: "2"
                }),
                _createElementVNode("circle", {
                  cx: "12",
                  cy: "12",
                  r: "2"
                }),
                _createElementVNode("circle", {
                  cx: "12",
                  cy: "19",
                  r: "2"
                })
              ]))
            ]),
            _createCommentVNode(" APP内容 "),
            _createElementVNode("div", { class: "phone-screen" }, [
              _createCommentVNode(" 板块1: 用户受理信息 "),
              (phoneTab === 'user')
                ? (_openBlock(), _createElementBlock("div", {
                    key: 0,
                    class: "app-page"
                  }, [
                    _createCommentVNode(" 套餐横幅卡片 "),
                    _createElementVNode("div", { class: "app-banner-card" }, [
                      _createElementVNode("div", { class: "app-banner-bg" }),
                      _createElementVNode("div", { class: "app-banner-content" }, [
                        _createElementVNode("div", { class: "app-banner-name" }, _toDisplayString(productInfo.prodPrcName), 1 /* TEXT */),
                        _createElementVNode("div", { class: "app-banner-fee" }, [
                          _createElementVNode("span", { class: "fee-num" }, _toDisplayString(previewFee), 1 /* TEXT */),
                          _createElementVNode("span", { class: "fee-unit" }, "元?月")
                        ]),
                        _createElementVNode("div", { class: "app-banner-tags" }, [
                          _createElementVNode("span", { class: "app-tag" }, _toDisplayString(previewFlowAmount) + _toDisplayString(previewFlowUnit) + "流量", 1 /* TEXT */),
                          _createElementVNode("span", { class: "app-tag" }, _toDisplayString(previewVoiceAmount) + "分钟", 1 /* TEXT */),
                          _createElementVNode("span", { class: "app-tag" }, _toDisplayString(dynamicPreview ? (previewRights.length ? previewRights.length + '项权益? : '视频权益') : '5G-A网络权益'), 1 /* TEXT */)
                        ])
                      ])
                    ]),
                    _createCommentVNode(" 资源详情卡片 "),
                    _createElementVNode("div", { class: "app-card" }, [
                      _createElementVNode("div", { class: "app-card-title" }, "套餐资源"),
                      _createElementVNode("div", { class: "app-resource-grid" }, [
                        _createElementVNode("div", { class: "app-resource-item" }, [
                          _createElementVNode("div", { class: "app-resource-icon flow" }, [
                            (_openBlock(), _createElementBlock("svg", {
                              width: "20",
                              height: "20",
                              viewBox: "0 0 24 24",
                              fill: "none",
                              stroke: "currentColor",
                              "stroke-width": "2"
                            }, [
                              _createElementVNode("path", { d: "M5 12h14M12 5l7 7-7 7" })
                            ]))
                          ]),
                          _createElementVNode("div", { class: "app-resource-num" }, [
                            _createTextVNode(_toDisplayString(previewFlowAmount), 1 /* TEXT */),
                            _createElementVNode("span", { class: "unit" }, _toDisplayString(previewFlowUnit), 1 /* TEXT */)
                          ]),
                          _createElementVNode("div", { class: "app-resource-label" }, "通用流量")
                        ]),
                        _createElementVNode("div", { class: "app-resource-item" }, [
                          _createElementVNode("div", { class: "app-resource-icon voice" }, [
                            (_openBlock(), _createElementBlock("svg", {
                              width: "20",
                              height: "20",
                              viewBox: "0 0 24 24",
                              fill: "none",
                              stroke: "currentColor",
                              "stroke-width": "2"
                            }, [
                              _createElementVNode("path", { d: "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" })
                            ]))
                          ]),
                          _createElementVNode("div", { class: "app-resource-num" }, [
                            _createTextVNode(_toDisplayString(previewVoiceAmount), 1 /* TEXT */),
                            _createElementVNode("span", { class: "unit" }, "分钟")
                          ]),
                          _createElementVNode("div", { class: "app-resource-label" }, "国内语音")
                        ]),
                        _createElementVNode("div", { class: "app-resource-item" }, [
                          _createElementVNode("div", { class: "app-resource-icon video" }, [
                            (_openBlock(), _createElementBlock("svg", {
                              width: "20",
                              height: "20",
                              viewBox: "0 0 24 24",
                              fill: "none",
                              stroke: "currentColor",
                              "stroke-width": "2"
                            }, [
                              _createElementVNode("rect", {
                                x: "2",
                                y: "3",
                                width: "20",
                                height: "14",
                                rx: "2"
                              }),
                              _createElementVNode("line", {
                                x1: "8",
                                y1: "21",
                                x2: "16",
                                y2: "21"
                              }),
                              _createElementVNode("line", {
                                x1: "12",
                                y1: "17",
                                x2: "12",
                                y2: "21"
                              })
                            ]))
                          ]),
                          _createElementVNode("div", { class: "app-resource-num" }, [
                            _createTextVNode(_toDisplayString(dynamicPreview ? previewRights.length : 2), 1 /* TEXT */),
                            _createElementVNode("span", { class: "unit" }, "户")
                          ]),
                          _createElementVNode("div", { class: "app-resource-label" }, _toDisplayString(dynamicPreview ? '权益资源' : '视频权益'), 1 /* TEXT */)
                        ])
                      ])
                    ]),
                    _createCommentVNode(" 权益详情 "),
                    _createElementVNode("div", { class: "app-card" }, [
                      _createElementVNode("div", { class: "app-card-title" }, "包含权益"),
                      _createElementVNode("div", { class: "app-equity-list" }, [
                        (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(equityList, (eq) => {
                          return (_openBlock(), _createElementBlock("div", {
                            class: "app-equity-item",
                            key: eq.name
                          }, [
                            _createElementVNode("div", {
                              class: "app-equity-icon",
                              style: _normalizeStyle({ background: eq.color })
                            }, _toDisplayString(eq.short), 5 /* TEXT, STYLE */),
                            _createElementVNode("div", { class: "app-equity-info" }, [
                              _createElementVNode("div", { class: "app-equity-name" }, _toDisplayString(eq.name), 1 /* TEXT */),
                              _createElementVNode("div", { class: "app-equity-desc" }, _toDisplayString(eq.desc), 1 /* TEXT */)
                            ])
                          ]))
                        }), 128 /* KEYED_FRAGMENT */))
                      ])
                    ]),
                    _createCommentVNode(" 费用说明 "),
                    _createElementVNode("div", { class: "app-card" }, [
                      _createElementVNode("div", { class: "app-card-title" }, "费用说明"),
                      _createElementVNode("div", { class: "app-fee-list" }, [
                        _createElementVNode("div", { class: "app-fee-row" }, [
                          _createElementVNode("span", null, "套餐月费"),
                          _createElementVNode("span", { class: "app-fee-val" }, _toDisplayString(previewFee) + ".00元/月", 1 /* TEXT */)
                        ]),
                        _createElementVNode("div", { class: "app-fee-row" }, [
                          _createElementVNode("span", null, "套外流量"),
                          _createElementVNode("span", { class: "app-fee-val" }, _toDisplayString(previewFlowOvercharge), 1 /* TEXT */)
                        ]),
                        _createElementVNode("div", { class: "app-fee-row" }, [
                          _createElementVNode("span", null, "套外语音"),
                          _createElementVNode("span", { class: "app-fee-val" }, _toDisplayString(previewVoiceOvercharge), 1 /* TEXT */)
                        ]),
                        previewSmsOvercharge
                          ? (_openBlock(), _createElementBlock("div", {
                              key: 0,
                              class: "app-fee-row"
                            }, [
                              _createElementVNode("span", null, "套外短信"),
                              _createElementVNode("span", { class: "app-fee-val" }, _toDisplayString(previewSmsOvercharge), 1 /* TEXT */)
                            ]))
                          : _createCommentVNode("v-if", true),
                        dynamicPreview
                          ? (_openBlock(), _createElementBlock("div", {
                              key: 1,
                              class: "app-fee-row"
                            }, [
                              _createElementVNode("span", null, "有效月"),
                              _createElementVNode("span", { class: "app-fee-val" }, _toDisplayString(optionLabel('validity', formData.validity) || '长期有效'), 1 /* TEXT */)
                            ]))
                          : _createCommentVNode("v-if", true),
                        (!dynamicPreview)
                          ? (_openBlock(), _createElementBlock("div", {
                              key: 2,
                              class: "app-fee-row highlight"
                            }, [
                              _createElementVNode("span", null, "收费方式"),
                              _createElementVNode("span", { class: "app-fee-val" }, "首月按天折算，次月起按月收取")
                            ]))
                          : _createCommentVNode("v-if", true)
                      ])
                    ]),
                    _createCommentVNode(" 规则说明 "),
                    _createElementVNode("div", { class: "app-card" }, [
                      _createElementVNode("div", { class: "app-card-title" }, "规则说明"),
                      _createElementVNode("div", { class: "app-rule-list" }, [
                        _createElementVNode("div", { class: "app-rule-item" }, [
                          _createElementVNode("span", { class: "app-rule-dot" }),
                          _createTextVNode("生效方式：" + _toDisplayString(optionLabel('effRule', formData.effRule) || '立即生效'), 1 /* TEXT */)
                        ]),
                        _createElementVNode("div", { class: "app-rule-item" }, [
                          _createElementVNode("span", { class: "app-rule-dot" }),
                          _createTextVNode("退订方式：" + _toDisplayString(optionLabel('cancelRule', formData.cancelRule) || '立即失效'), 1 /* TEXT */)
                        ]),
                        (!dynamicPreview)
                          ? (_openBlock(), _createElementBlock("div", {
                              key: 0,
                              class: "app-rule-item"
                            }, [
                              _createElementVNode("span", { class: "app-rule-dot" }),
                              _createTextVNode("流量可结转至次月")
                            ]))
                          : _createCommentVNode("v-if", true),
                        _createElementVNode("div", { class: "app-rule-item" }, [
                          _createElementVNode("span", { class: "app-rule-dot" }),
                          _createTextVNode("使用范围：" + _toDisplayString((Array.isArray(formData.applicableArea) ? formData.applicableArea.map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('。) : optionLabel('applicableArea', formData.applicableArea)) || '四川省内'), 1 /* TEXT */)
                        ]),
                        dialogueMode
                          ? (_openBlock(), _createElementBlock("div", {
                              key: 1,
                              class: "app-rule-item"
                            }, [
                              _createElementVNode("span", { class: "app-rule-dot" }),
                              _createTextVNode("前项限制：" + _toDisplayString((Array.isArray(formData.preLimitTable) ? formData.preLimitTable : []).map((r) => r.tipMsg).filter(Boolean).join('，?) || '目标客群为在校大学生，允许办理?), 1 /* TEXT */)
                            ]))
                          : (_openBlock(), _createElementBlock("div", {
                              key: 2,
                              class: "app-rule-item"
                            }, [
                              _createElementVNode("span", { class: "app-rule-dot" }),
                              _createTextVNode("新老商品互斥，不可同时订购")
                            ]))
                      ])
                    ]),
                    _createCommentVNode(" 办理按钮 "),
                    _createElementVNode("div", { class: "app-action-bar" }, [
                      _createElementVNode("button", { class: "app-btn-secondary" }, "加入购物转"),
                      _createElementVNode("button", { class: "app-btn-primary" }, "立即办理")
                    ])
                  ]))
                : _createCommentVNode("v-if", true),
              _createCommentVNode(" 板块2: 免填 "),
              (phoneTab === 'freefill')
                ? (_openBlock(), _createElementBlock("div", {
                    key: 1,
                    class: "app-page"
                  }, [
                    _createElementVNode("div", { class: "app-card" }, [
                      _createElementVNode("div", { class: "app-card-title" }, "免填单信息"),
                      _createElementVNode("div", { class: "app-formfill-list" }, [
                        (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(freeFillItems, (item) => {
                          return (_openBlock(), _createElementBlock("div", {
                            class: "app-formfill-row",
                            key: item.label
                          }, [
                            _createElementVNode("span", { class: "app-formfill-label" }, _toDisplayString(item.label), 1 /* TEXT */),
                            _createElementVNode("span", {
                              class: _normalizeClass(["app-formfill-value", { highlight: item.highlight }])
                            }, _toDisplayString(item.value), 3 /* TEXT, CLASS */)
                          ]))
                        }), 128 /* KEYED_FRAGMENT */))
                      ])
                    ]),
                    (!dynamicPreview)
                      ? (_openBlock(), _createElementBlock("div", {
                          key: 0,
                          class: "app-card"
                        }, [
                          _createElementVNode("div", { class: "app-card-title" }, "优惠信息"),
                          _createElementVNode("div", { class: "app-discount-box" }, [
                            _createElementVNode("div", { class: "app-discount-tag" }, "首月半价"),
                            _createElementVNode("div", { class: "app-discount-detail" }, [
                              _createElementVNode("div", { class: "app-discount-title" }, "新用户专属"),
                              _createElementVNode("div", { class: "app-discount-desc" }, "首月仅需29.50元，次月起恢复?9元?月")
                            ])
                          ])
                        ]))
                      : _createCommentVNode("v-if", true),
                    _createElementVNode("div", { class: "app-action-bar" }, [
                      _createElementVNode("button", { class: "app-btn-primary full" }, "确认受理")
                    ])
                  ]))
                : _createCommentVNode("v-if", true),
              _createCommentVNode(" 板块3: 短信模板预览 "),
              (phoneTab === 'sms')
                ? (_openBlock(), _createElementBlock("div", {
                    key: 2,
                    class: "app-page"
                  }, [
                    _createElementVNode("div", { class: "app-sms-chat" }, [
                      _createElementVNode("div", { class: "app-sms-time" }, "今天 " + _toDisplayString(phoneTime), 1 /* TEXT */),
                      (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(smsItems, (item, idx) => {
                        return (_openBlock(), _createElementBlock("div", {
                          class: "app-sms-msg",
                          key: idx
                        }, [
                          _createElementVNode("div", { class: "app-sms-avatar" }, [
                            (_openBlock(), _createElementBlock("svg", {
                              width: "20",
                              height: "20",
                              viewBox: "0 0 24 24",
                              fill: "none",
                              stroke: "currentColor",
                              "stroke-width": "2"
                            }, [
                              _createElementVNode("path", { d: "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" })
                            ]))
                          ]),
                          _createElementVNode("div", { class: "app-sms-bubble-wrap" }, [
                            _createElementVNode("div", { class: "app-sms-label" }, _toDisplayString(item.label), 1 /* TEXT */),
                            _createElementVNode("div", { class: "app-sms-bubble" }, _toDisplayString(item.value), 1 /* TEXT */)
                          ])
                        ]))
                      }), 128 /* KEYED_FRAGMENT */))
                    ])
                  ]))
                : _createCommentVNode("v-if", true)
            ]),
            _createCommentVNode(" 底部Tab导航 "),
            _createElementVNode("div", { class: "phone-tabbar" }, [
              _createElementVNode("div", {
                class: _normalizeClass(["phone-tab-item", { active: phoneTab === 'user' }]),
                onClick: $event => (phoneTab = 'user')
              }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "20",
                  height: "20",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" }),
                  _createElementVNode("circle", {
                    cx: "12",
                    cy: "7",
                    r: "4"
                  })
                ])),
                _createElementVNode("span", null, "受理信息")
              ], 10 /* CLASS, PROPS */, ["onClick"]),
              _createElementVNode("div", {
                class: _normalizeClass(["phone-tab-item", { active: phoneTab === 'freefill' }]),
                onClick: $event => (phoneTab = 'freefill')
              }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "20",
                  height: "20",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" }),
                  _createElementVNode("polyline", { points: "14 2 14 8 20 8" })
                ])),
                _createElementVNode("span", null, "免填单")
              ], 10 /* CLASS, PROPS */, ["onClick"]),
              _createElementVNode("div", {
                class: _normalizeClass(["phone-tab-item", { active: phoneTab === 'sms' }]),
                onClick: $event => (phoneTab = 'sms')
              }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "20",
                  height: "20",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2"
                }, [
                  _createElementVNode("path", { d: "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" })
                ])),
                _createElementVNode("span", null, "短信预览")
              ], 10 /* CLASS, PROPS */, ["onClick"])
            ]),
            _createCommentVNode(" 手机Home "),
            _createElementVNode("div", { class: "phone-home-indicator" })
          ])
        ], 512 /* NEED_PATCH */), [
          [_vShow, activeTab === 'summary']
        ])
      ]),
      _createElementVNode("div", { class: "config-footer" }, [
        _createCommentVNode(" 批量配置导航：第x/共y + 上一个'下一个'"),
        batchMode
          ? (_openBlock(), _createElementBlock("div", {
              key: 0,
              class: "batch-nav"
            }, [
              _createElementVNode("button", {
                class: "batch-nav-btn",
                disabled: !batchContext.hasPrev || submitted,
                onClick: $event => ($emit('go-prev')),
                title: batchContext.hasPrev ? '上一个草稿商品' : '已是第一个'
              }, [
                (_openBlock(), _createElementBlock("svg", {
                  width: "13",
                  height: "13",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2.5"
                }, [
                  _createElementVNode("polyline", { points: "15 18 9 12 15 6" })
                ])),
                _createTextVNode(" 上一个 ")
              ], 8 /* PROPS */, ["disabled", "onClick", "title"]),
              _createElementVNode("span", { class: "batch-nav-count" }, "第'" + _toDisplayString(batchContext.index) + " / " + _toDisplayString(batchContext.total) + " 户", 1 /* TEXT */),
              _createElementVNode("button", {
                class: "batch-nav-btn",
                disabled: !batchContext.hasNext || submitted,
                onClick: $event => ($emit('go-next')),
                title: batchContext.hasNext ? '下一个草稿商品' : '已是最后一个'
              }, [
                _createTextVNode(" 下一个 "),
                (_openBlock(), _createElementBlock("svg", {
                  width: "13",
                  height: "13",
                  viewBox: "0 0 24 24",
                  fill: "none",
                  stroke: "currentColor",
                  "stroke-width": "2.5"
                }, [
                  _createElementVNode("polyline", { points: "9 18 15 12 9 6" })
                ]))
              ], 8 /* PROPS */, ["disabled", "onClick", "title"])
            ]))
          : _createCommentVNode("v-if", true),
        (!submitted)
          ? (_openBlock(), _createElementBlock(_Fragment, { key: 1 }, [
              _createElementVNode("div", { class: "footer-left" }, [
                _createElementVNode("button", {
                  class: "footer-btn btn-draft",
                  onClick: saveDraft
                }, [
                  (_openBlock(), _createElementBlock("svg", {
                    width: "14",
                    height: "14",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    "stroke-width": "2"
                  }, [
                    _createElementVNode("path", { d: "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" }),
                    _createElementVNode("polyline", { points: "17 21 17 13 7 13 7 21" }),
                    _createElementVNode("polyline", { points: "7 3 7 8 15 8" })
                  ])),
                  _createTextVNode(" 保存草稿' ")
                ], 8 /* PROPS */, ["onClick"]),
                _createElementVNode("button", {
                  class: "footer-btn btn-check",
                  onClick: showAudit
                }, [
                  (_openBlock(), _createElementBlock("svg", {
                    width: "14",
                    height: "14",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    "stroke-width": "2"
                  }, [
                    _createElementVNode("path", { d: "M9 11l3 3L22 4" }),
                    _createElementVNode("path", { d: "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" })
                  ])),
                  _createTextVNode(" 智能稽核 ")
                ], 8 /* PROPS */, ["onClick"])
              ]),
              _createElementVNode("div", { class: "footer-right" }, [
                _createElementVNode("button", {
                  class: "btn-submit",
                  onClick: onSubmit
                }, [
                  (_openBlock(), _createElementBlock("svg", {
                    width: "14",
                    height: "14",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    "stroke-width": "2"
                  }, [
                    _createElementVNode("line", {
                      x1: "22",
                      y1: "2",
                      x2: "11",
                      y2: "13"
                    }),
                    _createElementVNode("polygon", { points: "22 2 15 22 11 13 2 9 22 2" })
                  ])),
                  _createTextVNode(" 提交配置 ")
                ], 8 /* PROPS */, ["onClick"])
              ])
            ], 64 /* STABLE_FRAGMENT */))
          : (_openBlock(), _createElementBlock("div", {
              key: 2,
              class: _normalizeClass(["footer-submitted", currentStatus])
            }, [
              (currentStatus === 'completed' || currentStatus === 'testing')
                ? (_openBlock(), _createElementBlock("svg", {
                    key: 0,
                    width: "16",
                    height: "16",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    "stroke-width": "2"
                  }, [
                    _createElementVNode("polyline", { points: "20 6 9 17 4 12" })
                  ]))
                : (_openBlock(), _createElementBlock("svg", {
                    key: 1,
                    width: "16",
                    height: "16",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    "stroke-width": "2"
                  }, [
                    _createElementVNode("circle", {
                      cx: "12",
                      cy: "12",
                      r: "10"
                    }),
                    _createElementVNode("polyline", { points: "12 6 12 12 16 14" })
                  ])),
              _createElementVNode("span", { class: "footer-submitted-text" }, _toDisplayString(footerSubmittedText), 1 /* TEXT */),
              _createElementVNode("span", { class: "footer-submitted-id" }, "工单编号：" + _toDisplayString(productInfo.workOrderId), 1 /* TEXT */)
            ], 2 /* CLASS */))
      ]),
      _createCommentVNode(" 智能稽核弹窗 "),
      _createVNode(_Transition, { name: "audit-fade" }, {
        default: _withCtx(() => [
          auditVisible
            ? (_openBlock(), _createElementBlock("div", {
                key: 0,
                class: "audit-overlay",
                onClick: _withModifiers($event => (auditVisible = false), ["self"])
              }, [
                _createElementVNode("div", { class: "audit-modal" }, [
                  _createElementVNode("div", { class: "audit-modal-header" }, [
                    _createElementVNode("span", { class: "audit-modal-title" }, "智能稽核"),
                    _createElementVNode("button", {
                      class: "audit-close",
                      onClick: $event => (auditVisible = false)
                    }, [
                      (_openBlock(), _createElementBlock("svg", {
                        width: "16",
                        height: "16",
                        viewBox: "0 0 24 24",
                        fill: "none",
                        stroke: "currentColor",
                        "stroke-width": "2"
                      }, [
                        _createElementVNode("line", {
                          x1: "18",
                          y1: "6",
                          x2: "6",
                          y2: "18"
                        }),
                        _createElementVNode("line", {
                          x1: "6",
                          y1: "6",
                          x2: "18",
                          y2: "18"
                        })
                      ]))
                    ], 8 /* PROPS */, ["onClick"])
                  ]),
                  _createElementVNode("div", { class: "audit-modal-body" }, [
                    auditLoading
                      ? (_openBlock(), _createElementBlock("div", {
                          key: 0,
                          class: "audit-loading"
                        }, [
                          _createElementVNode("div", { class: "audit-spinner" }),
                          _createElementVNode("p", null, _toDisplayString(auditProgressText), 1 /* TEXT */)
                        ]))
                      : (_openBlock(), _createElementBlock("div", {
                          key: 1,
                          class: "audit-results"
                        }, [
                          (_openBlock(true), _createElementBlock(_Fragment, null, _renderList(auditResults, (item, idx) => {
                            return (_openBlock(), _createElementBlock("div", {
                              key: idx,
                              class: _normalizeClass(["audit-item", item.type])
                            }, [
                              _createElementVNode("div", { class: "audit-icon" }, [
                                (item.type === 'success')
                                  ? (_openBlock(), _createElementBlock("svg", {
                                      key: 0,
                                      width: "16",
                                      height: "16",
                                      viewBox: "0 0 24 24",
                                      fill: "none",
                                      stroke: "currentColor",
                                      "stroke-width": "2"
                                    }, [
                                      _createElementVNode("polyline", { points: "20 6 9 17 4 12" })
                                    ]))
                                  : (_openBlock(), _createElementBlock("svg", {
                                      key: 1,
                                      width: "16",
                                      height: "16",
                                      viewBox: "0 0 24 24",
                                      fill: "none",
                                      stroke: "currentColor",
                                      "stroke-width": "2"
                                    }, [
                                      _createElementVNode("circle", {
                                        cx: "12",
                                        cy: "12",
                                        r: "10"
                                      }),
                                      _createElementVNode("line", {
                                        x1: "15",
                                        y1: "9",
                                        x2: "9",
                                        y2: "15"
                                      }),
                                      _createElementVNode("line", {
                                        x1: "9",
                                        y1: "9",
                                        x2: "15",
                                        y2: "15"
                                      })
                                    ]))
                              ]),
                              _createElementVNode("div", { class: "audit-text" }, [
                                _createElementVNode("div", { class: "audit-item-title" }, _toDisplayString(item.title), 1 /* TEXT */),
                                _createElementVNode("div", { class: "audit-item-desc" }, _toDisplayString(item.desc), 1 /* TEXT */)
                              ])
                            ], 2 /* CLASS */))
                          }), 128 /* KEYED_FRAGMENT */))
                        ]))
                  ])
                ])
              ], 8 /* PROPS */, ["onClick"]))
            : _createCommentVNode("v-if", true)
        ]),
        _: 1 /* STABLE */
      })
    ], 4 /* STYLE */))
  }
}