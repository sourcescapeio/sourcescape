import Hotkeys from 'react-hot-keys';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

import { DragDropContext, DropResult } from 'react-beautiful-dnd';
import { HighlightedCodeBlockComponent } from 'components/shared/HighlightedCodeBlock';

const HighlightSpanTemp = (props: any) => (<span>{props.children}</span>)
const CodeBlock = HighlightedCodeBlockComponent(HighlightSpanTemp);

const DISABLED_COLOR = 'rgba(167, 182, 194, 0.4)';
const HOVERED_COLOR = 'rgba(167, 182, 194)';

export function BuilderConsoleComponent(props: {
  context: any
  hovered: boolean,
  setHovered: (_: boolean) => void,
  setDragging: (_: boolean) => void
  // sub
  targeting: React.ReactNode
}) {
  const hasDisplay = props.context.display.length > 0;
  const display = hasDisplay ? props.context.display + "\n\n..." : props.context.display + "Start querying here..."
  const end_index = hasDisplay ? props.context.display.length + 5 : props.context.display.length + 22;

  const data = {
    display,
    nodes: [
      ...props.context.highlights,
      {
        start_index: props.context.display.length + (hasDisplay ? 2 : 0),
        end_index,
        id: 0,
        bodyId: 0,
        type: 'body',
      }
    ]
  }

  return <Hotkeys
    keyName="command+k,command+shift+p"
    // onKeyDown={this.onKeyDown}
  >
    <Container style={{
      backgroundColor: 'rgb(40, 52, 71)',
      minWidth: '100%',
      paddingLeft: 35,
      paddingRight: 35}}
      onMouseOver={() => props.setHovered(true)}
      onMouseOut={() => props.setHovered(false)}
    >
      {/* <Row style={{backgroundColor: 'rgb(40, 52, 71)', marginLeft: 2, height: 40, marginRight: 0}}>
        <Col style={{
          textAlign: 'right',
          paddingLeft: 0,
          paddingRight: 0,
          // display: (!this.state.hovered && 'none'),
        }}>
          <AnchorButton minimal={true} onClick={this.props.toggleIndexingPanel}>
            { this.props.isIndexing ? <Spinner size={15}/> : <Icon icon="download" />}
          </AnchorButton>
          <Popover position="bottom-right" minimal={true}>
            <AnchorButton minimal={true}>
              <Icon style={{
                color: this.state.hovered ? null : DISABLED_COLOR
              }} icon="more"/>
            </AnchorButton>
            <Menu>
              <MenuItem
                icon="th"
                text="Create report..."
                disabled={!this.props.context.selected}
                onClick={this.props.toggleReportsModal}
              />
              <MenuDivider />
              <MenuItem
                icon="console"
                text="Command Bar..."
                onClick={this.props.toggleBar}
                label="cmd+shift+p"
              />
              <MenuItem
                icon="console"
                text="Indexing Panel..."
                onClick={this.props.toggleIndexingPanel}
                label="cmd+i"
              />
              <MenuItem
                icon="floppy-disk"
                text="Save Query..."
                disabled={this.props.emptyContext}
                onClick={this.props.toggleSaveQueryModal}
                label="cmd+s"
              />
              <MenuItem
                icon="floppy-disk"
                text="Save Targeting..."
                disabled={this.props.emptyContext}
                onClick={this.props.toggleSaveTargetingModal}
                label="cmd+shift+s"
              />                
              <MenuDivider />
              <MenuItem
                icon="refresh"
                text="Refresh"
                onClick={this.props.refreshQuery}
              />              
              <MenuItem
                icon="build"
                text="Debug..."
                onClick={this.props.toggleDebug}
              />
              <MenuItem icon="cross" intent="danger" text="Reset" onClick={() => (this.props.reset())}/>
            </Menu>
          </Popover>
        </Col>
      </Row> */}
      <Row>
        <Col style={{minHeight: 100}}>
          <DragDropContext
            onDragStart={() => (props.setDragging(true))}
            // onDragUpdate={(update) => {
            //   console.warn(update);
            // }}
            onDragEnd={(update: DropResult) => {
              props.setDragging(false)

              // verify parents
              const { draggableId, destination } = update;

              if (destination && destination.droppableId) {
                const [sourceId, sourceIndex] = draggableId.split("|");
                const [destId, destIndex] = destination.droppableId.split("|");

                if ((sourceId === destId) && (sourceIndex !== destIndex)) {
                  // this.prependIndex(sourceId, parseInt(sourceIndex), parseInt(destIndex));
                }
              }
            }}
          >
            <CodeBlock
              language="typescript"
              text={data.display}
              nodes={data.nodes}
              edges={[]}
              snippetStartLine={0}
              hideLineNumbers={true}
              componentProps={{
                // // actions
                // deleteItem: this.deleteItem,
                // deleteEdge: this.deleteEdge,
                // setName: this.setName,
                // unsetName: this.unsetName,
                // setIndex: this.setIndex,
                // unsetIndex: this.unsetIndex,
                // setAlias: this.setAlias,
                // addArg: this.addArg,
                // //
                // references,
                // selected: this.props.selected,
                // select: this.props.select,
                // deselect: this.deselect,
                // hovered: this.props.hovered,
                // setHovered: this.props.setHovered,
                // // setBooleanState: this.props.setBooleanState,
                // //
                // // draft js stuff specifically for body
                // bodyId: this.state.bodyId,
                // setBody: this.setBody,
                // clearBody: this.clearBody,
                // appendCurrentBody: this.appendCurrentBody,
                // suppressClear: this.suppressClear,
                // unsuppressClear: this.unsuppressClear,
                // //
                // isDragging: this.state.isDragging,
                // // drag: this.props.drag,
                // //
                // trace: this.props.trace,
                // debug: this.props.debug,
                // // global state to move to redux
                // editorState: this.state.editorState,
                // editorStateUpdate: this.state.editorStateUpdate, // used to avoid editorState comparison
                // blockText: this.state.blockText,
                // onChange: this.onChange,
                // suggestions,
                // selectedIndex: this.state.selectedIndex,
                // suggestionUp: this.suggestionUp,
                // suggestionDown: this.suggestionDown,
                // suggestionEnter: this.suggestionEnter,
                // suggestionFill: this.suggestionFill,
              }}
            />
          </DragDropContext>
        </Col>
      </Row>
      <Row style={{backgroundColor: 'rgb(40, 52, 71)', marginLeft: 2, height: 40, marginRight: 0}}>
        <Col style={{
          paddingLeft: 0,
          paddingRight: 0,
          color: props.hovered ? HOVERED_COLOR : DISABLED_COLOR,
        }}>
          {/* <QueryConsoleDisplay
            selectedLanguage={this.props.selectedLanguage}
            languages={this.props.languages}
            selectLanguage={this.props.selectLanguage}
            savedQuery={this.props.savedQuery}
            cache={this.props.cache}
            latestCacheUpdate={this.props.latestCacheUpdate}
          /> */}
          <div>Test</div>
        </Col>
        <Col style={{
          textAlign: 'right',
          paddingLeft: 0,
          paddingRight: 0,
          color: props.hovered ? HOVERED_COLOR : DISABLED_COLOR,
        }}>
          {props.targeting}
        </Col>
      </Row>
    </Container>
  </Hotkeys>
}